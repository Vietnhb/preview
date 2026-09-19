package com.example.backend.dto.auth;

import lombok.Getter;

@Getter
public class SignupRequest {
    private String email;
    private String fullName;
    private String password;
}
