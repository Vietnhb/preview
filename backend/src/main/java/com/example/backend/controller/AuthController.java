package com.example.backend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.SignupRequest;
import com.example.backend.service.AuthService;

import lombok.AllArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import com.example.backend.repository.LicensePlanRepository;
import com.example.backend.entity.LicensePlan;
import java.util.List;

@RestController
@RequestMapping("/api/auth/")
@AllArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final LicensePlanRepository licensePlans;

    @GetMapping("plans")
    public List<LicensePlan> plans() {
        return licensePlans.findByActiveTrueOrderByAnnualPriceVndAsc();
    }

    @PostMapping("login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @PostMapping("signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("Signup Thanh Cong");
    }
}
