package com.search_recommendation_engine.repository;

import com.search_recommendation_engine.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

}