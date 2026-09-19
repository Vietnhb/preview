package com.example.backend.controller.auth;

import com.example.backend.dto.school.LicenseStatusResponse;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.school.LicenseCheckService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.user.UserResponse;
import com.example.backend.dto.user.ChangePasswordRequest;
import com.example.backend.dto.user.UpdateAvatarRequest;
import com.example.backend.dto.user.UpdateProfileRequest;
import com.example.backend.dto.school.StudentOptionResponse;
import com.example.backend.service.account.UserService;

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
    private final com.example.backend.service.account.CurrentUserService currentUserService;
    private final com.example.backend.service.school.LicenseCheckService licenseCheckService;

    @GetMapping("me/license")
    public com.example.backend.dto.school.LicenseStatusResponse license() {
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
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public List<StudentOptionResponse> getStudents() {
        return userService.getAssignableStudents();
    }

}
