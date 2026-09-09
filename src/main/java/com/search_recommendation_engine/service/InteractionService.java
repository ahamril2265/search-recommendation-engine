package com.search_recommendation_engine.service;

import com.search_recommendation_engine.dto.InteractionRequestDTO;
import com.search_recommendation_engine.dto.InteractionResponseDTO;

import java.util.List;

public interface InteractionService {
    InteractionResponseDTO logInteraction(InteractionRequestDTO request);
    List<InteractionResponseDTO> getInteractionsByUser(Long userId);
    List<InteractionResponseDTO> getInteractionsByProduct(Long productId);
}