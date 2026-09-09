package com.search_recommendation_engine.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1) // runs before ProductSeeder — index should exist before anything tries to write to it later
public class ProductIndexInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexInitializer.class);
    private static final String INDEX_NAME = "products";

    private final ElasticsearchClient client;

    public ProductIndexInitializer(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean exists = client.indices()
                .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                .value();

        if (exists) {
            log.info("Elasticsearch index '{}' already exists — skipping creation.", INDEX_NAME);
            return;
        }

        TypeMapping mapping = TypeMapping.of(m -> m
                .properties("id", Property.of(p -> p.long_(l -> l)))

                // text field for fuzzy/partial search, with a .keyword multi-field
                // for exact-match sorting or aggregation
                .properties("name", Property.of(p -> p
                        .text(t -> t
                                .fields("keyword", Property.of(kp -> kp.keyword(k -> k)))
                        )
                ))

                .properties("description", Property.of(p -> p.text(t -> t)))

                .properties("price", Property.of(p -> p.double_(d -> d)))

                // keyword — exact filtering only, no fuzzy matching
                .properties("categoryName", Property.of(p -> p.keyword(k -> k)))
                .properties("tags", Property.of(p -> p.keyword(k -> k)))

                .properties("popularityScore", Property.of(p -> p.long_(l -> l)))
                .properties("stockQuantity", Property.of(p -> p.integer(i -> i)))
        );

        CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .mappings(mapping)
        );

        client.indices().create(request);
        log.info("Created Elasticsearch index '{}' with explicit mapping.", INDEX_NAME);
    }
}