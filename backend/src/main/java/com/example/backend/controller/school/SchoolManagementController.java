package com.example.backend.controller.school;

import com.example.backend.dto.admin.CreateManagedUserRequest;
import com.example.backend.dto.admin.UpdateManagedUserRequest;
import com.example.backend.dto.admin.UserStatusResponse;
import com.example.backend.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
        return adminService.createUser(new CreateManagedUserRequest(request.email(), request.password(),
                request.fullName(), request.role(), schoolId.toString()));
    }

    @PutMapping("/{userId}")
    public UserStatusResponse update(@PathVariable UUID schoolId, @PathVariable Integer userId,
                                     @Valid @RequestBody UpdateManagedUserRequest request) {
        adminService.requireUserInSchool(schoolId, userId);
        return adminService.updateUser(userId,
                new UpdateManagedUserRequest(request.fullName(), request.role(), schoolId.toString()));
    }

    @PutMapping("/{userId}/suspend")
    public UserStatusResponse suspend(@PathVariable UUID schoolId, @PathVariable Integer userId) {
        adminService.requireUserInSchool(schoolId, userId);
        return adminService.setActive(userId, false);
    }

    @PutMapping("/{userId}/restore")
    public UserStatusResponse restore(@PathVariable UUID schoolId, @PathVariable Integer userId) {
        adminService.requireUserInSchool(schoolId, userId);
        return adminService.setActive(userId, true);
    }
}
