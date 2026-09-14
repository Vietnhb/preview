package com.example.backend.service;

import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.SignupRequest;
import com.example.backend.dto.UserResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is suspended");
        }
        if (!matchesPassword(password, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }
        user.setLastLogin(java.time.Instant.now());
        userRepository.save(user);
        String role = user.getRole() == null ? "TEACHER" : user.getRole().getName();
        return new LoginResponse(jwtUtil.generateToken(user.getEmail(), role),
                new UserResponse(user.getId(), user.getEmail(), user.getFullName(), role));
    }

    public void signup(SignupRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null
                || request.getEmail().isBlank() || request.getPassword().length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email and password with at least 8 characters are required");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        Role role = roleRepository.findByName("TEACHER")
                .orElseGet(() -> roleRepository.findByName("GUEST")
                        .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Default role is missing")));
        User user = new User();
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setFullName(request.getFullName() == null ? "PhysLive user" : request.getFullName().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setActive(true);
        userRepository.save(user);
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        return storedPassword.startsWith("$2")
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : rawPassword.equals(storedPassword);
    }
}
