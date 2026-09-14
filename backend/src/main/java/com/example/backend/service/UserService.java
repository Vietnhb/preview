package com.example.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.backend.dto.UserResponse;
import com.example.backend.dto.StudentOptionResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.UserRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UserService {
    UserRepository userRepository;

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Khong Thay User"));
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().getName());
    }

    public List<UserResponse> getAllUser() {
        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole().getName()))
                .toList();
    }

    public List<StudentOptionResponse> getActiveStudents() {
        return userRepository.findActiveByRoleName("STUDENT").stream()
                .map(user -> new StudentOptionResponse(user.getId(), user.getFullName()))
                .toList();
    }
}
