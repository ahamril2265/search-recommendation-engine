package com.search_recommendation_engine.repository;

import com.search_recommendation_engine.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    List<Interaction> findByUserId(Long userId);
    List<Interaction> findByProductId(Long productId);

    @Query("SELECT i.product.id, COUNT(i) FROM Interaction i GROUP BY i.product.id ORDER BY COUNT(i) DESC")
    List<Object[]> findProductInteractionCounts();

    @Query("SELECT i.product.id, COUNT(i) FROM Interaction i WHERE i.product.category.id = :categoryId GROUP BY i.product.id ORDER BY COUNT(i) DESC")
    List<Object[]> findProductInteractionCountsByCategory(@Param("categoryId") Long categoryId);

}