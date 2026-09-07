package com.search_recommendation_engine.repository;

import com.search_recommendation_engine.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    long countByCategoryId(Long categoryId);
}