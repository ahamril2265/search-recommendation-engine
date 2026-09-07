package com.search_recommendation_engine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponseDTO(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        String categoryName,
        List<String> tags,
        long popularityScore,
        LocalDateTime createdAt
) {}