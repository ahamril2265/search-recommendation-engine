package com.search_recommendation_engine.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.search_recommendation_engine.document.ProductDocument;
import com.search_recommendation_engine.dto.ProductSearchResultDTO;
import com.search_recommendation_engine.dto.SearchResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SearchServiceImpl implements SearchService {

    private static final String INDEX_NAME = "products";

    private final ElasticsearchClient client;

    public SearchServiceImpl(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    @Cacheable(
        value = "productSearch",
        key = "#query + ':' + #category + ':' + #minPrice + ':' + #maxPrice + ':' + #page + ':' + #size"
    )
    public SearchResponseDTO searchProducts(String query, String category, BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            boolQuery.must(m -> m
                    .multiMatch(mm -> mm
                            .query(query)
                            .fields("name", "description")
                            .fuzziness("AUTO")
                    )
            );

            if (category != null && !category.isBlank()) {
                boolQuery.filter(f -> f
                        .term(t -> t.field("categoryName").value(category))
                );
            }

            // NOTE: elasticsearch-java 8.15+ changed RangeQuery to a typed union —
            // you must pick .number()/.date()/.term() before field()/gte()/lte()
            // are available, and they take plain Java types now instead of JsonData
            if (minPrice != null || maxPrice != null) {
                final BigDecimal min = minPrice;
                final BigDecimal max = maxPrice;
                boolQuery.filter(f -> f
                        .range(r -> r
                                .number(n -> {
                                    n.field("price");
                                    if (min != null) n.gte(min.doubleValue());
                                    if (max != null) n.lte(max.doubleValue());
                                    return n;
                                })
                        )
                );
            }

            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.bool(boolQuery.build()))
                    .from(page * size)
                    .size(size)
            );

            SearchResponse<ProductDocument> response = client.search(request, ProductDocument.class);

            List<ProductSearchResultDTO> results = response.hits().hits().stream()
                    .map(this::mapHitToDTO)
                    .toList();

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            return new SearchResponseDTO(results, totalHits, page, size);

        } catch (Exception e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    private ProductSearchResultDTO mapHitToDTO(Hit<ProductDocument> hit) {
        ProductDocument doc = hit.source();
        double score = hit.score() != null ? hit.score() : 0.0;

        return new ProductSearchResultDTO(
                doc.getId(),
                doc.getName(),
                doc.getDescription(),
                doc.getPrice(),
                doc.getCategoryName(),
                doc.getTags(),
                doc.getPopularityScore(),
                score
        );
    }
}