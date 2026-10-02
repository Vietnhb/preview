package com.example.backend.system.account.controller;

import com.example.backend.system.account.dto.CreateManagedUserRequest;
import com.example.backend.system.account.dto.ResetManagedPasswordRequest;
import com.example.backend.system.account.dto.UpdateManagedUserRequest;
import com.example.backend.system.account.dto.UserStatusResponse;
import com.example.backend.system.account.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public UserStatusResponse updateUser(@PathVariable Integer id, @Valid @RequestBody UpdateManagedUserRequest request) {
        return adminService.updateUser(id, request);
    }

    @PostMapping("/users/{id}/reset-password")
    public UserStatusResponse resetPassword(@PathVariable Integer id,
                                           @Valid @RequestBody ResetManagedPasswordRequest request) {
        return adminService.resetPassword(id, request.newPassword());
    }

    @PutMapping("/users/{id}/suspend")
    public UserStatusResponse suspend(@PathVariable Integer id) {
        return adminService.setActive(id, false);
    }

    @PutMapping("/users/{id}/restore")
    public UserStatusResponse restore(@PathVariable Integer id) {
        return adminService.setActive(id, true);
    }

}
