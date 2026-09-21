package com.example.backend.security;


import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.backend.config.properties.SecurityProperties;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.dto.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.AllArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Security configuration for B2B role-based access control.
 *
 * Roles:
 * - ADMIN: Platform admin, manages all schools
 * - CONTENT_REVIEWER: Reviews shared simulations
 * - SCHOOL_MANAGER: Manages one school (users, classes)
 * - TEACHER: Creates simulations, teaches classes
 * - STUDENT: Takes classes, submits work
 */
@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(response.getOutputStream(),
                                    new ErrorResponse(HttpServletResponse.SC_UNAUTHORIZED,
                                            "Authentication is required"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(response.getOutputStream(),
                                    new ErrorResponse(HttpServletResponse.SC_FORBIDDEN,
                                            "You do not have permission for this action"));
                        }))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/auth/**", "/ws/**", "/actuator/health").permitAll()

                        // User profile (all authenticated users)
                        .requestMatchers("/api/user/me", "/api/user/profile/**").authenticated()

                        // Platform admin only
                        .requestMatchers("/api/admin/**").hasRole(RoleName.ADMIN.name())
                        .requestMatchers("/api/user/all").hasRole(RoleName.ADMIN.name())
                        .requestMatchers("/api/user/students").hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/schools/*/activate", "/api/schools/*/deactivate").hasRole(RoleName.ADMIN.name())

                        // Content Reviewer (platform-level content moderation)
                        .requestMatchers("/api/reviewer/**").hasAnyRole(RoleName.CONTENT_REVIEWER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/simulations/*/approve", "/api/simulations/*/reject").hasAnyRole(RoleName.CONTENT_REVIEWER.name(), RoleName.ADMIN.name())

                        // School Manager (school-level management)
                        .requestMatchers("/api/schools/*/users/**").hasAnyRole(RoleName.SCHOOL_MANAGER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/schools/*/classes/**").hasAnyRole(RoleName.SCHOOL_MANAGER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/schools/*/reports/**").hasAnyRole(RoleName.SCHOOL_MANAGER.name(), RoleName.ADMIN.name())

                        .requestMatchers("/api/problems/**").hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())

                        // Teacher (content creation + teaching)
                        .requestMatchers("/api/simulations/create").hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/assignments/**").hasAnyRole(RoleName.TEACHER.name(), RoleName.STUDENT.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/classes/*/students").hasAnyRole(RoleName.TEACHER.name(), RoleName.SCHOOL_MANAGER.name(), RoleName.ADMIN.name())

                        // Student (learning + submissions)
                        .requestMatchers("/api/student/**").hasRole(RoleName.STUDENT.name())
                        .requestMatchers("/api/support/admin/**").hasRole(RoleName.ADMIN.name())
                        .requestMatchers("/api/support/**").authenticated()

                        // School billing
                        .requestMatchers("/api/school/billing", "/api/school/billing/**")
                                .hasRole(RoleName.SCHOOL_MANAGER.name())

                        // Assignments: keep teacher and student operations separate.
                        .requestMatchers("/api/assignments/mine/teacher", "/api/assignments/mine/teacher/classes",
                                "/api/assignments/*/submissions", "/api/assignments/*/report",
                                "/api/assignments/*/submissions/*/grade", "/api/assignments/*/submissions/*/reopen")
                                .hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/assignments/mine/student", "/api/assignments/*/predictions",
                                "/api/assignments/*/submit", "/api/assignments/*/simulation",
                                "/api/assignments/*/simulation/adjust")
                                .hasRole(RoleName.STUDENT.name())
                        .requestMatchers("/api/assignments", "/api/assignments/**")
                                .hasAnyRole(RoleName.TEACHER.name(), RoleName.STUDENT.name(), RoleName.ADMIN.name())

                        // Shared library (view: all auth users, submit: teachers only)
                        .requestMatchers("/api/library/folders", "/api/library/folders/**")
                                .hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers(HttpMethod.POST, "/api/library", "/api/library/**")
                                .hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/library", "/api/library/**")
                                .hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/library", "/api/library/**")
                                .hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/library/mine").hasAnyRole(RoleName.TEACHER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/library", "/api/library/**").authenticated()

                        // Schemas (CONTENT_REVIEWER + ADMIN manage, others view approved only)
                        .requestMatchers(HttpMethod.POST, "/api/schemas", "/api/schemas/**")
                                .hasAnyRole(RoleName.CONTENT_REVIEWER.name(), RoleName.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/schemas", "/api/schemas/**")
                                .hasAnyRole(RoleName.CONTENT_REVIEWER.name(), RoleName.ADMIN.name())
                        .requestMatchers("/api/schemas", "/api/schemas/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated())

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(securityProperties.passwordStrength());
    }
}
