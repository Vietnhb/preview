package com.example.backend.security;

import com.example.backend.base.web.dto.ErrorResponse;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.service.LicenseCheckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private static final String ACCOUNT_LOCKED_MESSAGE =
            "Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên.";
    private static final String SCHOOL_DISABLED_MESSAGE =
            "Trường đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.";
    private static final String LICENSE_REQUIRED_MESSAGE =
            "Gói của trường chưa có hiệu lực. Vui lòng liên hệ quản lý trường.";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final com.example.backend.system.school.service.LicenseCheckService licenseCheckService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Claims claims;
            try {
                claims = jwtUtil.extractClaims(authHeader.substring(7));
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            String email = claims.getSubject();
            var currentUser = userRepository.findByEmail(email);
            if (currentUser.isPresent() && !Boolean.TRUE.equals(currentUser.get().getActive())) {
                writeForbidden(response, ACCOUNT_LOCKED_MESSAGE);
                return;
            }
            if (currentUser.isPresent() && currentUser.get().getSchool() != null
                    && !currentUser.get().getSchool().isActive()) {
                writeForbidden(response, SCHOOL_DISABLED_MESSAGE);
                return;
            }
            if (currentUser.isPresent() && currentUser.get().getRole() != null) {
                var user = currentUser.get();
                RoleName currentRole = RoleName.from(user.getRole().getName()).orElse(null);
                if (currentRole == null) {
                    writeForbidden(response, "Account role is not configured");
                    return;
                }
                String role = currentRole.name();
                boolean schoolRole = RoleName.from(role).map(RoleName::isSchoolRole).orElse(false);
                boolean write = !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
                String path = request.getRequestURI().substring(request.getContextPath().length());
                boolean passwordLifecyclePath = isPasswordLifecyclePath(request.getMethod(), path);
                if (user.isMustChangePassword() && !passwordLifecyclePath) {
                    SecurityContextHolder.clearContext();
                    writePasswordChangeRequired(response);
                    return;
                }
                boolean licenseActive = licenseCheckService.isLicenseActive(user);
                if (!passwordLifecyclePath && (RoleName.STAFF.matches(role) || RoleName.STUDENT.matches(role)) && !licenseActive) {
                    writeForbidden(response, LICENSE_REQUIRED_MESSAGE);
                    return;
                }
                if (!passwordLifecyclePath && RoleName.SCHOOL.matches(role) && !licenseActive && !isManagerBillingPath(path)) {
                    writeForbidden(response, "Vui lòng mua hoặc gia hạn gói để tiếp tục sử dụng PhysLive.");
                    return;
                }
                boolean managerRenewalRequest = RoleName.SCHOOL.matches(role)
                        && "POST".equals(request.getMethod())
                        && ("/api/school/billing/quote".equals(path)
                                || "/api/school/billing/checkout".equals(path));
                if (schoolRole && write && path.startsWith("/api/")
                        && !path.startsWith("/api/auth/") && !path.startsWith("/api/user/me/")
                        && !managerRenewalRequest
                        && !licenseActive) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                            HttpServletResponse.SC_FORBIDDEN, "School license does not allow writes"));
                    return;
                }
                String authority = currentRole.authority();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null,
                                List.of(new SimpleGrantedAuthority(authority)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isManagerBillingPath(String path) {
        return path.startsWith("/api/auth/")
                || "/api/user/me".equals(path)
                || "/api/user/me/license".equals(path)
                || "/api/school/billing".equals(path)
                || path.startsWith("/api/school/billing/");
    }

    private static boolean isPasswordLifecyclePath(String method, String path) {
        return "GET".equals(method) && "/api/user/me".equals(path)
                || "PUT".equals(method) && "/api/user/me/password".equals(path)
                || "POST".equals(method) && "/api/auth/logout".equals(path);
    }

    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(HttpServletResponse.SC_FORBIDDEN,
                "Vui lòng đổi mật khẩu khởi tạo trước khi sử dụng hệ thống.", "PASSWORD_CHANGE_REQUIRED", null));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                HttpServletResponse.SC_FORBIDDEN, message));
    }
}
