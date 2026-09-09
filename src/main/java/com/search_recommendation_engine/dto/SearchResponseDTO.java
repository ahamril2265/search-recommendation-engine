package com.search_recommendation_engine.dto;

import java.util.List;

public record SearchResponseDTO(
        List<ProductSearchResultDTO> results,
        long totalHits,
        int page,
        int size
) {}