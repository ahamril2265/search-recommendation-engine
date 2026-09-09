package com.search_recommendation_engine.dto;

import com.search_recommendation_engine.entity.InteractionType;
import jakarta.validation.constraints.NotNull;

public record InteractionRequestDTO(
        
        @NotNull(message = "User ID is required")
        Long userId,

        @NotNull(message = "Product ID is required")
        Long productId,

        @NotNull(message = "Interaction type is required")
        InteractionType type,


        Integer ratingValue
) {}