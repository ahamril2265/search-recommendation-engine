package com.search_recommendation_engine.config;

import com.search_recommendation_engine.entity.*;
import com.search_recommendation_engine.repository.CategoryRepository;
import com.search_recommendation_engine.repository.InteractionRepository;
import com.search_recommendation_engine.repository.ProductRepository;
import com.search_recommendation_engine.repository.UserRepository;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
@Order(4) // after ProductSeeder(2) and ElasticsearchSyncRunner(3) — needs real products/categories to exist first
public class InteractionSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InteractionSeeder.class);
    private static final int BATCH_SIZE = 500;
    private static final int NUM_SYNTHETIC_USERS = 50;
    private static final int MIN_INTERACTIONS_PER_USER = 15;
    private static final int MAX_INTERACTIONS_PER_USER = 40;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InteractionRepository interactionRepository;
    private final Faker faker = new Faker();

    public InteractionSeeder(UserRepository userRepository, ProductRepository productRepository,
                               CategoryRepository categoryRepository, InteractionRepository interactionRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.interactionRepository = interactionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (interactionRepository.count() > 2) {
            // ">2" not ">0" — deliberately allows the handful of real interactions
            // from manual API testing to already exist without blocking this seed
            log.info("Interactions already exist ({} rows) — skipping synthetic seed.", interactionRepository.count());
            return;
        }

        // Group real products by category so each synthetic user's "taste"
        // maps to an actual, coherent set of products
        List<Product> allProducts = productRepository.findAll();
        Map<Long, List<Product>> productsByCategory = allProducts.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory().getId()));
        List<Long> categoryIds = new ArrayList<>(productsByCategory.keySet());

        if (categoryIds.isEmpty()) {
            log.warn("No products/categories found — skipping interaction seed.");
            return;
        }

        List<User> syntheticUsers = createSyntheticUsers();
        List<Interaction> batch = new ArrayList<>(BATCH_SIZE);
        int totalCreated = 0;

        for (User user : syntheticUsers) {
            // Each user "prefers" 1-2 categories — this is what gives collaborative
            // filtering real signal: users with overlapping preferred categories
            // will show up as similar to each other
            int preferenceCount = ThreadLocalRandom.current().nextInt(1, 3);
            List<Long> preferredCategories = pickRandomCategories(categoryIds, preferenceCount);

            List<Product> preferredProducts = preferredCategories.stream()
                    .flatMap(catId -> productsByCategory.get(catId).stream())
                    .collect(Collectors.toList());

            int interactionCount = ThreadLocalRandom.current().nextInt(MIN_INTERACTIONS_PER_USER, MAX_INTERACTIONS_PER_USER + 1);

            for (int i = 0; i < interactionCount; i++) {
                Product product;
                // 85% of the time, interact within preferred categories (the "signal");
                // 15% of the time, interact with something random (the "noise" —
                // real users don't stick perfectly to one taste, and pure noise-free
                // data would make the algorithm look artificially perfect)
                if (ThreadLocalRandom.current().nextInt(100) < 85 && !preferredProducts.isEmpty()) {
                    product = preferredProducts.get(ThreadLocalRandom.current().nextInt(preferredProducts.size()));
                } else {
                    product = allProducts.get(ThreadLocalRandom.current().nextInt(allProducts.size()));
                }

                Interaction interaction = new Interaction();
                interaction.setUser(user);
                interaction.setProduct(product);

                InteractionType type = weightedRandomType();
                interaction.setInteraction(type);
                interaction.setRatingValue(type == InteractionType.RATING
                        ? ThreadLocalRandom.current().nextInt(1, 6)
                        : null);

                batch.add(interaction);
                totalCreated++;

                if (batch.size() >= BATCH_SIZE) {
                    interactionRepository.saveAll(batch);
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            interactionRepository.saveAll(batch);
        }

        log.info("Interaction seeding complete: {} synthetic users, {} interactions created.",
                syntheticUsers.size(), totalCreated);
    }

    private List<User> createSyntheticUsers() {
        List<User> users = new ArrayList<>(NUM_SYNTHETIC_USERS);
        for (int i = 0; i < NUM_SYNTHETIC_USERS; i++) {
            User user = new User();
            String username = faker.internet().username() + "_" + i;
            user.setUsername(username);
            user.setEmail(username + "@example.com");
            users.add(userRepository.save(user));
        }
        return users;
    }

    private List<Long> pickRandomCategories(List<Long> categoryIds, int count) {
        List<Long> shuffled = new ArrayList<>(categoryIds);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private InteractionType weightedRandomType() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 60) return InteractionType.VIEW;
        if (roll < 85) return InteractionType.CART;
        if (roll < 95) return InteractionType.PURCHASE;
        return InteractionType.RATING;
    }
}