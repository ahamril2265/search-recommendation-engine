package com.search_recommendation_engine.controller;

import com.search_recommendation_engine.dto.InteractionRequestDTO;
import com.search_recommendation_engine.dto.InteractionResponseDTO;
import com.search_recommendation_engine.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping
    public ResponseEntity<InteractionResponseDTO> logInteraction(@Valid @RequestBody InteractionRequestDTO request) {
        InteractionResponseDTO created = interactionService.logInteraction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<InteractionResponseDTO>> getInteractionsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(interactionService.getInteractionsByUser(userId));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<InteractionResponseDTO>> getInteractionsByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(interactionService.getInteractionsByProduct(productId));
    }
}