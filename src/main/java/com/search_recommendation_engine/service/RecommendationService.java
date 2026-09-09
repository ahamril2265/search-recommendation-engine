package com.search_recommendation_engine.service;

import com.search_recommendation_engine.dto.RecommendationResponseDTO;

import java.util.List;

public interface RecommendationService {
    List<RecommendationResponseDTO> getRecommendations(Long userId, int limit);
}