package com.search_recommendation_engine.dto;

import com.search_recommendation_engine.entity.InteractionType;

import java.time.LocalDateTime;

public record InteractionResponseDTO(
        Long id,
        Long userId,
        Long productId,
        String productName,
        InteractionType type,
        Integer ratingValue,
        LocalDateTime createdAt
) {}