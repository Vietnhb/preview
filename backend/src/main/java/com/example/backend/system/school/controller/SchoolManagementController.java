package com.example.backend.system.school.controller;

import com.example.backend.system.account.dto.CreateManagedUserRequest;
import com.example.backend.system.account.dto.ResetManagedPasswordRequest;
import com.example.backend.system.account.dto.UpdateManagedUserRequest;
import com.example.backend.system.account.dto.UserStatusResponse;
import com.example.backend.system.account.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schools/{schoolId}/users")
@RequiredArgsConstructor
public class SchoolManagementController {
    private final AdminService adminService;

    @GetMapping
    public List<UserStatusResponse> list(@PathVariable UUID schoolId) {
        return adminService.usersForSchool(schoolId);
    }

    @PostMapping
    public UserStatusResponse create(@PathVariable UUID schoolId,
                                     @Valid @RequestBody CreateManagedUserRequest request) {
        return adminService.createUserForSchool(schoolId, request);
    }

    @PutMapping("/{userId}")
    public UserStatusResponse update(@PathVariable UUID schoolId, @PathVariable Integer userId,
                                     @Valid @RequestBody UpdateManagedUserRequest request) {
        return adminService.updateUserForSchool(schoolId, userId, request);
    }

    @PostMapping("/{userId}/reset-password")
    public UserStatusResponse resetPassword(@PathVariable UUID schoolId, @PathVariable Integer userId,
                                           @Valid @RequestBody ResetManagedPasswordRequest request) {
        return adminService.resetPasswordForSchool(schoolId, userId, request.newPassword());
    }

    @PutMapping("/{userId}/suspend")
    public UserStatusResponse suspend(@PathVariable UUID schoolId, @PathVariable Integer userId) {
        return adminService.setActiveForSchool(schoolId, userId, false);
    }

    @PutMapping("/{userId}/restore")
    public UserStatusResponse restore(@PathVariable UUID schoolId, @PathVariable Integer userId) {
        return adminService.setActiveForSchool(schoolId, userId, true);
    }
}
