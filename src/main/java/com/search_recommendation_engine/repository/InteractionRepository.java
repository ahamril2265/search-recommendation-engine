package com.search_recommendation_engine.repository;

import com.search_recommendation_engine.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

}