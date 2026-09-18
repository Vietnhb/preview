package com.example.backend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.UserResponse;
import com.example.backend.dto.ChangePasswordRequest;
import com.example.backend.dto.UpdateAvatarRequest;
import com.example.backend.dto.UpdateProfileRequest;
import com.example.backend.dto.StudentOptionResponse;
import com.example.backend.service.UserService;

import lombok.AllArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/user/")
@AllArgsConstructor
public class UserController {
    private final UserService userService;
    private final com.example.backend.service.CurrentUserService currentUserService;
    private final com.example.backend.service.LicenseCheckService licenseCheckService;

    @GetMapping("me/license")
    public com.example.backend.dto.LicenseStatusResponse license() {
        return licenseCheckService.status(currentUserService.requireCurrentUser());
    }

    private final PasswordEncoder passwordEncoder;

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

    @PutMapping("me/profile")
    public UserResponse updateProfile(Authentication authentication, @jakarta.validation.Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authentication.getName(), request.fullName(), request.dateOfBirth());
    }

    @PutMapping("me/password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @jakarta.validation.Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword(), passwordEncoder);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("me/avatar")
    public UserResponse updateAvatar(Authentication authentication, @jakarta.validation.Valid @RequestBody UpdateAvatarRequest request) {
        return userService.updateAvatar(authentication.getName(), request.avatarUrl());
    }

    @GetMapping("students")
    @PreAuthorize("hasRole('TEACHER')")
    public List<StudentOptionResponse> getStudents() {
        return userService.getActiveStudents();
    }

}
