package com.example.backend.service.auth;


import com.example.backend.dto.auth.LoginResponse;
import com.example.backend.dto.auth.SignupRequest;
import com.example.backend.dto.user.UserResponse;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.entity.school.LicensePlan;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.LicensePlanRepository;
import com.example.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LicensePlanRepository licensePlanRepository;

    @Transactional(readOnly = true)
    public List<LicensePlan> plans() {
        return licensePlanRepository.findByActiveTrueOrderByAnnualPriceVndAsc();
    }

    public LoginResponse login(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));

        // Only an explicit active flag allows authentication. Legacy null rows
        // are treated as locked instead of silently bypassing this check.
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // Check if school is deactivated
        if (user.getSchool() != null && Boolean.FALSE.equals(user.getSchool().isActive())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "School account is deactivated. Please contact administrator.");
        }

        // Note: License expiry does NOT block login (grace mode allows read-only access)

        if (!matchesPassword(password, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }

        user.setLastLogin(java.time.Instant.now());
        userRepository.save(user);

        String role = user.getRole() == null ? null : user.getRole().getName();
        if (RoleName.from(role).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account role is not configured");
        }
        return new LoginResponse(jwtUtil.generateToken(user.getEmail(), role),
                new UserResponse(user.getId(), user.getEmail(), user.getFullName(), role, user.getDateOfBirth(), user.getAvatarUrl(), user.getSchool() == null ? null : user.getSchool().getId()));
    }

    public void signup(SignupRequest request) {
        throw new ApiException(HttpStatus.FORBIDDEN,
                "Self-registration is disabled. Contact your school manager for an account.");
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) return false;
        return storedPassword.startsWith("$2")
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : rawPassword.equals(storedPassword);
    }
}
