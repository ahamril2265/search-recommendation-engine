package com.search_recommendation_engine.dto;

import java.math.BigDecimal;

public record RecommendationResponseDTO(
        Long productId,
        String name,
        BigDecimal price,
        String categoryName,
        double score
) {}