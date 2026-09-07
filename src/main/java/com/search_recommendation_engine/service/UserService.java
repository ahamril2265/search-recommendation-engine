package com.search_recommendation_engine.service;

import com.search_recommendation_engine.dto.UserRequestDTO;
import com.search_recommendation_engine.dto.UserResponseDTO;

import java.util.List;

public interface UserService {
    UserResponseDTO createUser(UserRequestDTO request);
    UserResponseDTO getUserById(Long id);
    List<UserResponseDTO> getAllUsers();
    UserResponseDTO updateUser(Long id, UserRequestDTO request);
    void deleteUser(Long id);
}