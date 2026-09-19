package com.example.backend.controller.admin;

import com.example.backend.dto.admin.CreateManagedUserRequest;
import com.example.backend.dto.admin.UserStatusResponse;
import com.example.backend.dto.admin.ValidationMetricsResponse;
import com.example.backend.dto.admin.TopicStatusResponse;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/users")
    public List<UserStatusResponse> users() {
        return adminService.users();
    }

    @PostMapping("/users")
    public UserStatusResponse createUser(@Valid @RequestBody CreateManagedUserRequest request) {
        return adminService.createUser(request);
    }

    @PutMapping("/users/{id}")
    public UserStatusResponse updateUser(@PathVariable Integer id, @Valid @RequestBody AdminService.UpdateUserRequest request) {
        return adminService.updateUser(id, request);
    }

    @PutMapping("/users/{id}/suspend")
    public UserStatusResponse suspend(@PathVariable Integer id) {
        return adminService.setActive(id, false);
    }

    @PutMapping("/users/{id}/restore")
    public UserStatusResponse restore(@PathVariable Integer id) {
        return adminService.setActive(id, true);
    }

    @PutMapping("/topics/{id}/toggle")
    public TopicStatusResponse toggleTopic(@PathVariable UUID id) {
        return adminService.toggleTopic(id);
    }

    @GetMapping("/metrics/validation")
    public ValidationMetricsResponse validationMetrics() {
        return adminService.validationMetrics();
    }
}
