package com.search_recommendation_engine.service;

import com.search_recommendation_engine.dto.RecommendationResponseDTO;
import com.search_recommendation_engine.entity.Interaction;
import com.search_recommendation_engine.entity.Product;
import com.search_recommendation_engine.exception.UserNotFoundException;
import com.search_recommendation_engine.repository.InteractionRepository;
import com.search_recommendation_engine.repository.ProductRepository;
import com.search_recommendation_engine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private final InteractionRepository interactionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public RecommendationServiceImpl(InteractionRepository interactionRepository,
                                       ProductRepository productRepository,
                                       UserRepository userRepository) {
        this.interactionRepository = interactionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public List<RecommendationResponseDTO> getRecommendations(Long userId, int limit) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        List<Interaction> allInteractions = interactionRepository.findAll();

        Map<Long, Map<Long, Double>> itemVectors = new HashMap<>();
        for (Interaction interaction : allInteractions) {
            Long productId = interaction.getProduct().getId();
            Long uid = interaction.getUser().getId();
            double weight = weightForInteraction(interaction);
            itemVectors.computeIfAbsent(productId, k -> new HashMap<>())
                    .merge(uid, weight, Double::sum);
        }

        Map<Long, Double> userInteractedProducts = allInteractions.stream()
                .filter(i -> i.getUser().getId().equals(userId))
                .collect(Collectors.toMap(
                        i -> i.getProduct().getId(),
                        this::weightForInteraction,
                        Double::sum
                ));

        // TIER 1: Pure cold-start — no interaction history at all
        if (userInteractedProducts.isEmpty()) {
            return getTrendingProducts(limit, Set.of());
        }

        // Normal collaborative filtering path
        Map<Long, Double> candidateScores = new HashMap<>();
        for (Map.Entry<Long, Double> userItem : userInteractedProducts.entrySet()) {
            Map<Long, Double> interactedVector = itemVectors.get(userItem.getKey());
            if (interactedVector == null) continue;

            for (Long candidateProductId : itemVectors.keySet()) {
                if (userInteractedProducts.containsKey(candidateProductId)) continue;
                double similarity = cosineSimilarity(interactedVector, itemVectors.get(candidateProductId));
                if (similarity > 0) {
                    candidateScores.merge(candidateProductId, similarity * userItem.getValue(), Double::sum);
                }
            }
        }

        // TIER 2: Has history, but collaborative filtering found zero overlap
        // (item cold-start — e.g. the user's only interacted products are
        // themselves isolated, with no shared users to compute similarity from)
        if (candidateScores.isEmpty()) {
            return getCategoryFallback(userId, userInteractedProducts.keySet(), limit);
        }

        return candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> toDto(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * TIER 1 fallback: globally trending products, ranked by total interaction
     * count across all users. Used when a user has no interaction history at all.
     */
    private List<RecommendationResponseDTO> getTrendingProducts(int limit, Set<Long> excludeIds) {
        return interactionRepository.findProductInteractionCounts().stream()
                .map(row -> Map.entry((Long) row[0], ((Number) row[1]).doubleValue()))
                .filter(entry -> !excludeIds.contains(entry.getKey()))
                .limit(limit)
                .map(entry -> toDto(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * TIER 2 fallback: trending products within the same category(ies) the user
     * has already shown interest in. Used when the user has history, but
     * collaborative filtering couldn't find any similarity signal to act on.
     */
    private List<RecommendationResponseDTO> getCategoryFallback(Long userId, Set<Long> alreadyInteractedProductIds, int limit) {
        Set<Long> userCategoryIds = alreadyInteractedProductIds.stream()
                .map(id -> productRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(p -> p.getCategory().getId())
                .collect(Collectors.toSet());

        List<RecommendationResponseDTO> results = new ArrayList<>();
        for (Long categoryId : userCategoryIds) {
            interactionRepository.findProductInteractionCountsByCategory(categoryId).stream()
                    .map(row -> Map.entry((Long) row[0], ((Number) row[1]).doubleValue()))
                    .filter(entry -> !alreadyInteractedProductIds.contains(entry.getKey()))
                    .map(entry -> toDto(entry.getKey(), entry.getValue()))
                    .filter(Objects::nonNull)
                    .forEach(results::add);
            if (results.size() >= limit) break;
        }

        // If even the category fallback comes up empty (extremely sparse data),
        // fall through to pure global trending as a last resort
        if (results.isEmpty()) {
            return getTrendingProducts(limit, alreadyInteractedProductIds);
        }

        return results.stream().limit(limit).toList();
    }

    private RecommendationResponseDTO toDto(Long productId, double score) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return null;
        return new RecommendationResponseDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory().getName(),
                score
        );
    }

    private double weightForInteraction(Interaction interaction) {
        return switch (interaction.getInteraction()) {
            case VIEW -> 1.0;
            case CART -> 3.0;
            case PURCHASE -> 5.0;
            case RATING -> interaction.getRatingValue() != null ? interaction.getRatingValue().doubleValue() : 3.0;
        };
    }

    private double cosineSimilarity(Map<Long, Double> vectorA, Map<Long, Double> vectorB) {
        double dotProduct = 0.0;
        for (Map.Entry<Long, Double> entry : vectorA.entrySet()) {
            Double bValue = vectorB.get(entry.getKey());
            if (bValue != null) dotProduct += entry.getValue() * bValue;
        }
        double normA = Math.sqrt(vectorA.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(vectorB.values().stream().mapToDouble(v -> v * v).sum());
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (normA * normB);
    }
}