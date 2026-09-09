package com.search_recommendation_engine.event;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.search_recommendation_engine.document.ProductDocument;
import com.search_recommendation_engine.entity.Product;
import com.search_recommendation_engine.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProductUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductUpdateConsumer.class);
    private static final String INDEX_NAME = "products";

    private final ProductRepository productRepository;
    private final ElasticsearchClient elasticsearchClient;
    private final CacheManager cacheManager;

    public ProductUpdateConsumer(ProductRepository productRepository,
                                   ElasticsearchClient elasticsearchClient,
                                   CacheManager cacheManager) {
        this.productRepository = productRepository;
        this.elasticsearchClient = elasticsearchClient;
        this.cacheManager = cacheManager;
    }

    @KafkaListener(topics = "product-updates", groupId = "search-engine-consumer-group")
    @Transactional
    public void handleProductUpdate(ProductUpdateEvent event) {
        log.info("Consumed event: {}", event);

        try {
            Product product = productRepository.findById(event.productId())
                    .orElse(null);

            if (product == null) {
                log.warn("Product {} from event not found in Postgres — skipping.", event.productId());
                return;
            }

            // Consistency double-check: re-apply the event's values to Postgres,
            // even though the REST layer already wrote them. Makes this consumer
            // correct even if a future event source isn't the REST API.
            if (event.newPrice() != null) {
                product.setPrice(event.newPrice());
            }
            if (event.newStockQuantity() != null) {
                product.setStockQuantity(event.newStockQuantity());
            }
            productRepository.save(product);

            // Reindex just this one product in Elasticsearch
            ProductDocument doc = new ProductDocument(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getCategory().getName(),
                    product.getTags(),
                    product.getPopularityScore(),
                    product.getStockQuantity()
            );

            elasticsearchClient.index(idx -> idx
                    .index(INDEX_NAME)
                    .id(String.valueOf(doc.getId()))
                    .document(doc)
            );

            // Invalidate the search cache. We can't target just the queries that
            // included this product — @Cacheable keys by query params, not by
            // product ID — so we clear the whole "productSearch" cache instead.
            // This is a coarse-grained but correct invalidation strategy: better
            // to serve a fresh result than a stale one, and search queries are
            // cheap to recompute compared to serving wrong data.
            var cache = cacheManager.getCache("productSearch");
            if (cache != null) {
                cache.clear();
            }

            log.info("Product {} updated in Postgres, reindexed in Elasticsearch, and search cache invalidated.", product.getId());

        } catch (Exception e) {
            log.error("Failed to process ProductUpdateEvent for product {}: {}", event.productId(), e.getMessage(), e);
        }
    }
}