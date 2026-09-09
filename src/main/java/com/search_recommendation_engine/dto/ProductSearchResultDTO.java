package com.search_recommendation_engine.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchResultDTO(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String categoryName,
        List<String> tags,
        long popularityScore,
        double relevanceScore
) {}