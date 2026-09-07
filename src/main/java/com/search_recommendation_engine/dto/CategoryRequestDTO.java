package com.search_recommendation_engine.dto;
import jakarta.validation.constraints.NotBlank;

public record CategoryRequestDTO(

        @NotBlank( message = "Category name is required.")
        String name,
        String description
) {}