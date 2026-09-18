package com.example.backend.security;

import com.example.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
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
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final com.example.backend.service.LicenseCheckService licenseCheckService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.extractClaims(authHeader.substring(7));
                String email = claims.getSubject();
                var currentUser = userRepository.findByEmail(email)
                        .filter(user -> Boolean.TRUE.equals(user.getActive()))
                        .filter(user -> user.getSchool() == null || user.getSchool().isActive());
                if (currentUser.isPresent() && currentUser.get().getRole() != null) {
                    var user = currentUser.get();
                    boolean schoolRole = com.example.backend.constants.RoleConstants.isSchoolRole(user.getRole().getName());
                    boolean write = !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    if (schoolRole && write && path.startsWith("/api/")
                            && !path.startsWith("/api/auth/") && !path.startsWith("/api/user/me/")
                            && !licenseCheckService.canPerformWriteOperations(user)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "School license does not allow writes");
                        return;
                    }
                    String role = currentUser.get().getRole().getName();
                    String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(email, null,
                                    List.of(new SimpleGrantedAuthority(authority)));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
