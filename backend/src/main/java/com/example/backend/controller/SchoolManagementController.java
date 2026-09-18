package com.example.backend.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.backend.dto.admin.CreateManagedUserRequest;
import com.example.backend.dto.admin.UserStatusResponse;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AdminService;
import com.example.backend.service.CurrentUserService;
import com.example.backend.service.RoleValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schools/{schoolId}/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_MANAGER')")
@RequiredArgsConstructor
public class SchoolManagementController {
    private final AdminService adminService;
    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final RoleValidationService roleValidation;

    @GetMapping
    public List<UserStatusResponse> list(@PathVariable UUID schoolId) {
        requireSchool(schoolId);
        return users.findBySchoolId(schoolId).stream().map(user -> new UserStatusResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().getName(),
                Boolean.TRUE.equals(user.getActive()), schoolId.toString(), user.getLastLogin(), user.getDateOfBirth())).toList();
    }

    @PostMapping
    public UserStatusResponse create(@PathVariable UUID schoolId,
                                     @Valid @RequestBody CreateManagedUserRequest request) {
        requireSchool(schoolId);
        return adminService.createUser(new CreateManagedUserRequest(request.email(), request.password(),
                request.fullName(), request.role(), schoolId.toString()));
    }

    @PutMapping("/{userId}")
    public UserStatusResponse update(@PathVariable UUID schoolId, @PathVariable Integer userId,
                                    @Valid @RequestBody AdminService.UpdateUserRequest request) {
        requireTarget(schoolId, userId);
        return adminService.updateUser(userId,
                new AdminService.UpdateUserRequest(request.fullName(), request.role(), schoolId.toString()));
    }

    @PutMapping("/{userId}/suspend")
    public UserStatusResponse suspend(@PathVariable UUID schoolId, @PathVariable Integer userId) {
        requireTarget(schoolId, userId);
        return adminService.setActive(userId, false);
    }

    @PutMapping("/{userId}/restore")
    public UserStatusResponse restore(@PathVariable UUID schoolId, @PathVariable Integer userId) {
        requireTarget(schoolId, userId);
        return adminService.setActive(userId, true);
    }

    private void requireSchool(UUID schoolId) {
        if (!roleValidation.canManageSchool(currentUserService.requireCurrentUser(), schoolId))
            throw new ApiException(HttpStatus.FORBIDDEN, "School access denied");
    }

    private void requireTarget(UUID schoolId, Integer userId) {
        requireSchool(schoolId);
        users.findById(userId).filter(user -> user.getSchool() != null && schoolId.equals(user.getSchool().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School user not found"));
    }
}
