package com.search_recommendation_engine.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.search_recommendation_engine.document.ProductDocument;
import com.search_recommendation_engine.entity.Product;
import com.search_recommendation_engine.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.ArrayList;

@Component
@Order(3) // after ProductIndexInitializer (1) and ProductSeeder (2) — index and Postgres data must exist first
public class ElasticsearchSyncRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSyncRunner.class);
    private static final String INDEX_NAME = "products";
    private static final int BATCH_SIZE = 500;

    private final ElasticsearchClient client;
    private final ProductRepository productRepository;

    public ElasticsearchSyncRunner(ElasticsearchClient client, ProductRepository productRepository) {
        this.client = client;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        long existingDocCount = client.count(c -> c.index(INDEX_NAME)).count();

        if (existingDocCount > 0) {
            log.info("Elasticsearch index '{}' already has {} documents — skipping sync.", INDEX_NAME, existingDocCount);
            return;
        }

        List<Product> allProducts = productRepository.findAll();
        log.info("Starting sync of {} products from Postgres to Elasticsearch...", allProducts.size());

        int totalIndexed = 0;
        int totalFailed = 0;

        for (int i = 0; i < allProducts.size(); i += BATCH_SIZE) {
            List<Product> batch = allProducts.subList(i, Math.min(i + BATCH_SIZE, allProducts.size()));

            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Product product : batch) {
                ProductDocument doc = mapToDocument(product);
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(String.valueOf(doc.getId()))
                                .document(doc)
                        )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());

            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        totalFailed++;
                        log.warn("Failed to index product id {}: {}", item.id(), item.error().reason());
                    } else {
                        totalIndexed++;
                    }
                }
            } else {
                totalIndexed += batch.size();
            }
        }

        log.info("Elasticsearch sync complete: {} indexed, {} failed.", totalIndexed, totalFailed);
    }

    private ProductDocument mapToDocument(Product product) {
        return new ProductDocument(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory().getName(),
                product.getTags(),
                product.getPopularityScore(),
                product.getStockQuantity(),
                buildNameSuggest(product.getName())   // <-- new argument, matches the new field
        );
    }

    private List<String> buildNameSuggest(String productName) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add(productName);
        for (String word : productName.split("\\s+")) {
            if (!suggestions.contains(word)) {
                suggestions.add(word);
            }
        }
        return suggestions;
    }
}