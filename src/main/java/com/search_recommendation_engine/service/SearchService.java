package com.search_recommendation_engine.service;

import com.search_recommendation_engine.dto.SearchResponseDTO;

import java.math.BigDecimal;

public interface SearchService {
    SearchResponseDTO searchProducts(String query, String category, BigDecimal minPrice, BigDecimal maxPrice, int page, int size);
}