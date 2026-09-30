package com.example.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import com.example.backend.entity.account.User;

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
    private String staffType;
    private boolean reviewerCanEdit;
    private boolean reviewerCanReview;

    public static UserResponse from(User user, boolean billingRequired) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole() == null ? "UNKNOWN" : user.getRole().getName(),
                user.getDateOfBirth(), user.getAvatarUrl(),
                user.getSchool() == null ? null : user.getSchool().getId(), billingRequired,
                user.isMustChangePassword(), user.getSchool() == null ? null : user.getSchool().getName(),
                Boolean.TRUE.equals(user.getActive()), user.getStaffType(),
                Boolean.TRUE.equals(user.getReviewerCanEdit()), Boolean.TRUE.equals(user.getReviewerCanReview()));
    }
}
