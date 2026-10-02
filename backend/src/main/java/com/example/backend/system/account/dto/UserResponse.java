package com.example.backend.system.account.dto;

import com.example.backend.system.account.model.entity.User;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {
    private Integer id;
    private String email;
    private String fullName;
    private String role;
    private LocalDate dateOfBirth;
    private String avatarUrl;
    private java.util.UUID schoolId;
    private boolean billingRequired;
    private boolean mustChangePassword;
    private String schoolName;
    private boolean active;
    /** Permission codes granted through user_permissions, e.g. TEACH, DEPARTMENT_HEAD_PHYSICS. */
    private java.util.List<String> permissions;

    public static UserResponse from(User user, boolean billingRequired) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole() == null ? "UNKNOWN" : user.getRole().getName(),
                user.getDateOfBirth(), user.getAvatarUrl(),
                user.getSchool() == null ? null : user.getSchool().getId(), billingRequired,
                user.isMustChangePassword(), user.getSchool() == null ? null : user.getSchool().getName(),
                Boolean.TRUE.equals(user.getActive()), user.permissionCodes());
    }
}
