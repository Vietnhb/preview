package com.example.backend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.UserResponse;
import com.example.backend.dto.StudentOptionResponse;
import com.example.backend.service.UserService;

import lombok.AllArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/user/")
@AllArgsConstructor
public class UserController {
    UserService userService;

    @GetMapping("me")
    public UserResponse getMe(Authentication authentication) {
        String email = authentication.getName();
        return userService.getCurrentUser(email);
    }

    @GetMapping("all")
    public ResponseEntity<List<UserResponse>> getUsers() {
        List<UserResponse> users = userService.getAllUser();
        return ResponseEntity.ok(users);
    }

    @GetMapping("students")
    @PreAuthorize("hasRole('TEACHER')")
    public List<StudentOptionResponse> getStudents() {
        return userService.getActiveStudents();
    }

}
