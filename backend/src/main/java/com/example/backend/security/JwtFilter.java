package com.example.backend.security;

import com.example.backend.service.school.LicenseCheckService;

import com.example.backend.repository.account.UserRepository;
import com.example.backend.dto.common.ErrorResponse;
import com.example.backend.entity.enums.RoleName;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final com.example.backend.service.school.LicenseCheckService licenseCheckService;
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
            var currentUser = userRepository.findByEmail(email)
                    // Legacy rows may have a null active flag; only an explicit
                    // false value means that the account has been suspended.
                    .filter(user -> !Boolean.FALSE.equals(user.getActive()))
                    .filter(user -> user.getSchool() == null || user.getSchool().isActive());
            if (currentUser.isPresent() && currentUser.get().getRole() != null) {
                var user = currentUser.get();
                String role = user.getRole().getName();
                boolean schoolRole = RoleName.from(role).map(RoleName::isSchoolRole).orElse(false);
                boolean write = !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
                String path = request.getRequestURI().substring(request.getContextPath().length());
                boolean managerRenewalRequest = RoleName.SCHOOL_MANAGER.matches(role)
                        && "POST".equals(request.getMethod())
                        && ("/api/school/billing/quote".equals(path)
                                || "/api/school/billing/checkout".equals(path));
                if (schoolRole && write && path.startsWith("/api/")
                        && !path.startsWith("/api/auth/") && !path.startsWith("/api/user/me/")
                        && !managerRenewalRequest
                        && !licenseCheckService.canPerformWriteOperations(user)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                            HttpServletResponse.SC_FORBIDDEN, "School license does not allow writes"));
                    return;
                }
                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null,
                                List.of(new SimpleGrantedAuthority(authority)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
