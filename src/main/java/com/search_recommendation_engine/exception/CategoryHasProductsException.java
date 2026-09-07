package com.search_recommendation_engine.exception;

public class CategoryHasProductsException extends RuntimeException {
    public CategoryHasProductsException(Long categoryId, long productCount) {
        super("Cannot delete category with id " + categoryId + " — " + productCount + " product(s) still reference it");
    }
}