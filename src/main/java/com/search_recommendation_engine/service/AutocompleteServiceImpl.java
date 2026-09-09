package com.search_recommendation_engine.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import com.search_recommendation_engine.document.ProductDocument;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AutocompleteServiceImpl implements AutocompleteService {

    private static final String INDEX_NAME = "products";

    private final ElasticsearchClient client;

    public AutocompleteServiceImpl(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public List<String> suggest(String prefix) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .suggest(su -> su
                            .suggesters("product-suggest", fs -> fs
                                    .prefix(prefix)
                                    .completion(c -> c
                                            .field("nameSuggest")
                                            .skipDuplicates(true)
                                            .size(10)
                                    )
                            )
                    )
            );

            SearchResponse<ProductDocument> response = client.search(request, ProductDocument.class);

            LinkedHashSet<String> suggestions = new LinkedHashSet<>();
            response.suggest().get("product-suggest").forEach(entry -> {
                for (CompletionSuggestOption<ProductDocument> option : entry.completion().options()) {
                    suggestions.add(option.text());
                }
            });

            return suggestions.stream().limit(10).toList();

        } catch (Exception e) {
            throw new RuntimeException("Autocomplete failed: " + e.getMessage(), e);
        }
    }
}