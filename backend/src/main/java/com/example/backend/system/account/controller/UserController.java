package com.example.backend.system.account.controller;

import com.example.backend.system.account.dto.ProfileRequest;
import com.example.backend.system.account.dto.UserResponse;
import com.example.backend.system.account.service.UserService;
import com.example.backend.system.school.dto.LicenseStatusResponse;
import com.example.backend.system.school.dto.SchoolClassContracts.StudentOptionResponse;
import com.example.backend.system.school.dto.SchoolClassContracts;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("me/license")
    public LicenseStatusResponse license() {
        return userService.license();
    }

    @GetMapping("me")
    public UserResponse getMe(Authentication authentication) {
        return userService.getCurrentUser(authentication.getName());
    }

    @PutMapping("me/profile")
    public UserResponse updateProfile(Authentication authentication, @Valid @RequestBody ProfileRequest.Details request) {
        return userService.updateProfile(authentication.getName(), request.fullName(), request.dateOfBirth());
    }

    @PutMapping("me/password")
    public UserResponse changePassword(Authentication authentication, @Valid @RequestBody ProfileRequest.Password request) {
        return userService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
    }

    @PutMapping("me/avatar")
    public UserResponse updateAvatar(Authentication authentication, @Valid @RequestBody ProfileRequest.Avatar request) {
        return userService.updateAvatar(authentication.getName(), request.avatarUrl());
    }

    @GetMapping("students")
    public List<StudentOptionResponse> getStudents() {
        return userService.getAssignableStudents();
    }
}
