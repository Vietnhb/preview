package com.example.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;

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
}
