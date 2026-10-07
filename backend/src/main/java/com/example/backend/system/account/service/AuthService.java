package com.example.backend.system.account.service;

import com.example.backend.exception.ApiException;
import com.example.backend.security.JwtUtil;
import com.example.backend.security.PasswordPolicy;
import com.example.backend.system.account.dto.LoginResponse;
import com.example.backend.system.account.dto.UserResponse;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.school.dto.SchoolPaymentContracts.LicensePlanResponse;
import com.example.backend.system.school.dto.SchoolPaymentContracts;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.repository.LicensePlanRepository;
import com.example.backend.system.school.service.LicenseCheckService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LicensePlanRepository licensePlanRepository;
    private final LicenseCheckService licenseCheckService;

    @Transactional(readOnly = true)
    public List<LicensePlanResponse> plans() {
        return licensePlanRepository.findByActiveTrueOrderByAnnualPriceVndAsc().stream()
                .map(LicensePlanResponse::from).toList();
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> ApiException.unauthorized("Email hoặc mật khẩu không đúng"));

        // Only an explicit active flag allows authentication. Legacy null rows
        // are treated as locked instead of silently bypassing this check.
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw ApiException.forbidden("Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // Check if school is deactivated
        if (user.getSchool() != null && Boolean.FALSE.equals(user.getSchool().isActive())) {
            throw ApiException.forbidden("Tài khoản trường đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.");
        }

        if (!PasswordPolicy.matches(password, user.getPassword(), passwordEncoder)) {
            throw ApiException.unauthorized("Email hoặc mật khẩu không đúng");
        }

        // Upgrade legacy credentials on successful authentication; never persist raw passwords again.
        if (!user.getPassword().startsWith("$2")) {
            PasswordPolicy.requireEncodable(password);
            user.setPassword(passwordEncoder.encode(password));
            user.setMustChangePassword(true);
        }

        String role = user.getRole() == null ? null : user.getRole().getName();
        RoleName roleName = RoleName.from(role)
                .orElseThrow(() -> ApiException.forbidden("Tài khoản chưa được thiết lập vai trò"));
        if (!user.isMustChangePassword() && (roleName == RoleName.STAFF || roleName == RoleName.STUDENT)
                && !licenseCheckService.isLicenseActive(user)) {
            throw ApiException.forbidden("Gói của trường chưa có hiệu lực. Vui lòng liên hệ quản lý trường.");
        }

        user.setLastLogin(java.time.Instant.now());
        userRepository.save(user);

        boolean billingRequired = roleName == RoleName.SCHOOL
                && !licenseCheckService.isLicenseActive(user);
        return new LoginResponse(jwtUtil.generateToken(user.getEmail(), role),
                UserResponse.from(user, billingRequired));
    }

}
