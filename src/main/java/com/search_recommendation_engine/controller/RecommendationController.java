package com.search_recommendation_engine.controller;

import com.search_recommendation_engine.dto.RecommendationResponseDTO;
import com.search_recommendation_engine.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<RecommendationResponseDTO>> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(recommendationService.getRecommendations(userId, limit));
    }
}