package com.search_recommendation_engine.config;

import com.search_recommendation_engine.entity.Category;
import com.search_recommendation_engine.entity.Product;
import com.search_recommendation_engine.repository.CategoryRepository;
import com.search_recommendation_engine.repository.ProductRepository;
import net.datafaker.Faker;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Order(2)
public class ProductSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);
    private static final int BATCH_SIZE = 500;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final Faker faker = new Faker();

    // Keyword -> Category mapping, checked against the product name (case-insensitive).
    // This turns "Laptop" into "Electronics" deterministically instead of random noise.
    private static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    static {
        CATEGORY_KEYWORDS.put("laptop", "Electronics");
        CATEGORY_KEYWORDS.put("phone", "Electronics");
        CATEGORY_KEYWORDS.put("monitor", "Electronics");
        CATEGORY_KEYWORDS.put("headphone", "Electronics");
        CATEGORY_KEYWORDS.put("camera", "Electronics");
        CATEGORY_KEYWORDS.put("tablet", "Electronics");
        CATEGORY_KEYWORDS.put("speaker", "Electronics");
        CATEGORY_KEYWORDS.put("mouse", "Electronics");
        CATEGORY_KEYWORDS.put("keyboard", "Electronics");
        CATEGORY_KEYWORDS.put("shirt", "Clothing");
        CATEGORY_KEYWORDS.put("jacket", "Clothing");
        CATEGORY_KEYWORDS.put("dress", "Clothing");
        CATEGORY_KEYWORDS.put("shoe", "Clothing");
        CATEGORY_KEYWORDS.put("jeans", "Clothing");
        CATEGORY_KEYWORDS.put("sofa", "Home & Furniture");
        CATEGORY_KEYWORDS.put("chair", "Home & Furniture");
        CATEGORY_KEYWORDS.put("table", "Home & Furniture");
        CATEGORY_KEYWORDS.put("lamp", "Home & Furniture");
        CATEGORY_KEYWORDS.put("blender", "Home Appliances");
        CATEGORY_KEYWORDS.put("microwave", "Home Appliances");
        CATEGORY_KEYWORDS.put("vacuum", "Home Appliances");
        CATEGORY_KEYWORDS.put("book", "Books");
        CATEGORY_KEYWORDS.put("novel", "Books");
        CATEGORY_KEYWORDS.put("ball", "Sports & Outdoors");
        CATEGORY_KEYWORDS.put("bike", "Sports & Outdoors");
        CATEGORY_KEYWORDS.put("racket", "Sports & Outdoors");
        CATEGORY_KEYWORDS.put("toy", "Toys & Games");
        CATEGORY_KEYWORDS.put("doll", "Toys & Games");
    }

    private static final List<String> FALLBACK_CATEGORIES = List.of(
            "Electronics", "Clothing", "Home & Furniture", "Home Appliances",
            "Books", "Sports & Outdoors", "Toys & Games", "Beauty & Personal Care", "Grocery"
    );

    private static final Map<String, List<String>> CATEGORY_TAGS = Map.of(
            "Electronics", List.of("wireless", "bluetooth", "rechargeable", "portable", "smart", "high-performance"),
            "Clothing", List.of("cotton", "breathable", "casual", "formal", "lightweight", "trendy"),
            "Home & Furniture", List.of("modern", "compact", "durable", "handcrafted", "space-saving"),
            "Home Appliances", List.of("energy-efficient", "compact", "automatic", "durable"),
            "Books", List.of("bestseller", "paperback", "hardcover", "illustrated"),
            "Sports & Outdoors", List.of("durable", "lightweight", "waterproof", "professional-grade"),
            "Toys & Games", List.of("educational", "kids-friendly", "colorful", "safe"),
            "Beauty & Personal Care", List.of("organic", "cruelty-free", "long-lasting", "gentle"),
            "Grocery", List.of("organic", "fresh", "gluten-free", "vegan")
    );

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
                    skippedCount++;
                    log.warn("Skipping row {} due to: {}", record.getRecordNumber(), e.getMessage());
                }

                if (batch.size() >= BATCH_SIZE) {
                    productRepository.saveAll(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                productRepository.saveAll(batch);
            }
        }

        log.info("Seeding complete: {} products inserted, {} rows skipped.", successCount, skippedCount);
    }

    private Product buildProduct(CSVRecord record, Map<String, Category> categoryCache) {
        // NOTE: confirm these column names match your actual CSV header exactly —
        // your file's real headers may be "Product Name"/"Price" (with spaces/capitals)
        // rather than snake_case, based on what you pasted earlier
        String name = record.get("Product Name");
        String rawPrice = record.get("Price");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing product name");
        }

        String categoryName = resolveCategory(name);
        Category category = categoryCache.computeIfAbsent(categoryName, this::findOrCreateCategory);

        Product product = new Product();
        List<String> tags = generateTags(categoryName);
        product.setName(name);
        product.setDescription(generateDescription(name, categoryName, tags));
        product.setPrice(parsePrice(rawPrice));
        product.setCategory(category);
        product.setStockQuantity(ThreadLocalRandom.current().nextInt(0, 500));
        product.setPopularityScore(0L);
        product.setTags(tags);

        return product;
    }

    private String resolveCategory(String productName) {
        String lowerName = productName.toLowerCase();
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (lowerName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // No keyword matched — fall back to a random (but still realistic) category
        return FALLBACK_CATEGORIES.get(ThreadLocalRandom.current().nextInt(FALLBACK_CATEGORIES.size()));
    }

    private String generateDescription(String productName, String categoryName, List<String> tags) {
        String material = faker.commerce().material();
        String sentence = faker.lorem().sentence(8);
        String tagPhrase = String.join(", ", tags);

        return String.format(
                "%s crafted with %s, featuring %s functionality. %s Ideal for %s.",
                productName, material, tagPhrase, sentence, categoryName.toLowerCase()
        );
    }

    private List<String> generateTags(String categoryName) {
        List<String> pool = CATEGORY_TAGS.getOrDefault(categoryName, List.of("popular", "quality", "value"));
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        int tagCount = Math.min(3, shuffled.size());
        return shuffled.subList(0, tagCount);
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
        String cleaned = rawPrice.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(cleaned);
    }
}