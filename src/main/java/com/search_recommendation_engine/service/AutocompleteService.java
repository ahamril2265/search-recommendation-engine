package com.search_recommendation_engine.service;

import java.util.List;

public interface AutocompleteService {
    List<String> suggest(String prefix);
}