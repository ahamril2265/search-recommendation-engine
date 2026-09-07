package com.search_recommendation_engine.config;

import com.search_recommendation_engine.entity.Category;
import com.search_recommendation_engine.entity.Product;
import com.search_recommendation_engine.repository.CategoryRepository;
import com.search_recommendation_engine.repository.ProductRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ProductSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);
    private static final int BATCH_SIZE = 500;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductSeeder(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (productRepository.count() > 0) {
            log.info("Products already exist ({} rows) — skipping seed.", productRepository.count());
            return;
        }

        Map<String, Category> categoryCache = new HashMap<>();
        List<Product> batch = new ArrayList<>(BATCH_SIZE);

        int successCount = 0;
        int skippedCount = 0;

        try (var reader = new InputStreamReader(
                new ClassPathResource("data/products.csv").getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                try {
                    Product product = buildProduct(record, categoryCache);
                    batch.add(product);
                    successCount++;
                } catch (Exception e) {
                    // Malformed row (bad price, missing field, etc.) — skip and keep going
                    // rather than letting one bad row kill the whole seed run
                    skippedCount++;
                    log.warn("Skipping row {} due to: {}", record.getRecordNumber(), e.getMessage());
                }

                if (batch.size() >= BATCH_SIZE) {
                    productRepository.saveAll(batch);
                    batch.clear();
                }
            }

            // Flush any remaining products that didn't fill a full batch
            if (!batch.isEmpty()) {
                productRepository.saveAll(batch);
            }
        }

        log.info("Seeding complete: {} products inserted, {} rows skipped.", successCount, skippedCount);
    }

    private Product buildProduct(CSVRecord record, Map<String, Category> categoryCache) {
        String categoryName = record.get("Product Category");
        String name = record.get("Product Name");
        String description = record.get("Product Description");
        String rawPrice = record.get("Price");

        if (categoryName == null || categoryName.isBlank()
                || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing required field (category or name)");
        }

        Category category = categoryCache.computeIfAbsent(categoryName, this::findOrCreateCategory);

        Product product = new Product();
        product.setName(name);
        product.setDescription(description != null ? description : "");
        product.setPrice(parsePrice(rawPrice));
        product.setCategory(category);
        // Dataset has no real inventory numbers — assign a plausible random stock level
        product.setStockQuantity(ThreadLocalRandom.current().nextInt(0, 500));
        product.setPopularityScore(0L);

        return product;
    }

    private Category findOrCreateCategory(String categoryName) {
        return categoryRepository.findByName(categoryName)
                .orElseGet(() -> {
                    Category newCategory = new Category();
                    newCategory.setName(categoryName);
                    return categoryRepository.save(newCategory);
                });
    }

    private BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return BigDecimal.ZERO;
        }
        // Strip common currency symbols/commas that show up in scraped CSV data
        String cleaned = rawPrice.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(cleaned);
    }
}