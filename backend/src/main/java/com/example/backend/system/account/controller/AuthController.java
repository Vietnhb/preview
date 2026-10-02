package com.example.backend.system.account.controller;

import com.example.backend.system.account.dto.LoginRequest;
import com.example.backend.system.account.dto.LoginResponse;
import com.example.backend.system.account.service.AuthService;
import com.example.backend.system.school.dto.SchoolPaymentContracts.LicensePlanResponse;
import com.example.backend.system.school.dto.SchoolPaymentContracts;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/")
@AllArgsConstructor
public class AuthController {
    private final AuthService authService;

    @GetMapping("plans")
    public List<LicensePlanResponse> plans() {
        return authService.plans();
    }

    @PostMapping("login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @PostMapping("logout")
    public ResponseEntity<Void> logout() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
