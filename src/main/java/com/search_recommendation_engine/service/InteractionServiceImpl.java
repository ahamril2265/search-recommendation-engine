package com.search_recommendation_engine.service;

import com.search_recommendation_engine.dto.InteractionRequestDTO;
import com.search_recommendation_engine.dto.InteractionResponseDTO;
import com.search_recommendation_engine.entity.Interaction;
import com.search_recommendation_engine.entity.InteractionType;
import com.search_recommendation_engine.entity.Product;
import com.search_recommendation_engine.entity.User;
import com.search_recommendation_engine.exception.ProductNotFoundException;
import com.search_recommendation_engine.exception.UserNotFoundException;
import com.search_recommendation_engine.repository.InteractionRepository;
import com.search_recommendation_engine.repository.ProductRepository;
import com.search_recommendation_engine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InteractionServiceImpl implements InteractionService {

    private final InteractionRepository interactionRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public InteractionServiceImpl(InteractionRepository interactionRepository,
                                    UserRepository userRepository,
                                    ProductRepository productRepository) {
        this.interactionRepository = interactionRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public InteractionResponseDTO logInteraction(InteractionRequestDTO request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        // Business-rule validation that Bean Validation annotations can't easily
        // express: ratingValue is required for RATING, meaningless otherwise
        if (request.type() == InteractionType.RATING && request.ratingValue() == null) {
            throw new IllegalArgumentException("ratingValue is required when type is RATING");
        }

        Interaction interaction = new Interaction();
        interaction.setUser(user);
        interaction.setProduct(product);
        interaction.setInteraction(request.type());
        interaction.setRatingValue(request.type() == InteractionType.RATING ? request.ratingValue() : null);

        Interaction saved = interactionRepository.save(interaction);
        return mapToResponseDTO(saved);
    }

    @Override
    public List<InteractionResponseDTO> getInteractionsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return interactionRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    @Override
    public List<InteractionResponseDTO> getInteractionsByProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return interactionRepository.findByProductId(productId)
                .stream()
                .map(this::mapToResponseDTO)
                .toList();
    }

    private InteractionResponseDTO mapToResponseDTO(Interaction interaction) {
        return new InteractionResponseDTO(
                interaction.getId(),
                interaction.getUser().getId(),
                interaction.getProduct().getId(),
                interaction.getProduct().getName(),
                interaction.getInteraction(),
                interaction.getRatingValue(),
                interaction.getCreatedAt()
        );
    }
}