package com.search_recommendation_engine.controller;

import com.search_recommendation_engine.service.AutocompleteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AutocompleteController {

    private final AutocompleteService autocompleteService;

    public AutocompleteController(AutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    @GetMapping("/api/search/autocomplete")
    public ResponseEntity<List<String>> autocomplete(@RequestParam String prefix) {
        return ResponseEntity.ok(autocompleteService.suggest(prefix));
    }
}