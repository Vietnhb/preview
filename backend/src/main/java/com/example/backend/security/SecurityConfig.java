package com.example.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.backend.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
@EnableMethodSecurity // Enables @PreAuthorize on methods
@AllArgsConstructor
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;

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
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/user/all").hasRole("ADMIN")
                        .requestMatchers("/api/schools/*/activate", "/api/schools/*/deactivate").hasRole("ADMIN")

                        // Content Reviewer (platform-level content moderation)
                        .requestMatchers("/api/reviewer/**").hasAnyRole("CONTENT_REVIEWER", "ADMIN")
                        .requestMatchers("/api/simulations/*/approve", "/api/simulations/*/reject").hasAnyRole("CONTENT_REVIEWER", "ADMIN")

                        // School Manager (school-level management)
                        .requestMatchers("/api/schools/*/users/**").hasAnyRole("SCHOOL_MANAGER", "ADMIN")
                        .requestMatchers("/api/schools/*/classes/**").hasAnyRole("SCHOOL_MANAGER", "ADMIN")
                        .requestMatchers("/api/schools/*/reports/**").hasAnyRole("SCHOOL_MANAGER", "ADMIN")

                        .requestMatchers("/api/problems/**").hasAnyRole("TEACHER", "ADMIN")

                        // Teacher (content creation + teaching)
                        .requestMatchers("/api/simulations/create").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/api/assignments/**").hasAnyRole("TEACHER", "STUDENT", "ADMIN")
                        .requestMatchers("/api/classes/*/students").hasAnyRole("TEACHER", "SCHOOL_MANAGER", "ADMIN")

                        // Student (learning + submissions)
                        .requestMatchers("/api/assignments/*/submit").hasAnyRole("STUDENT", "ADMIN")
                        .requestMatchers("/api/student/**").hasAnyRole("STUDENT", "ADMIN")
                        .requestMatchers("/api/support/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/support/**").authenticated()

                        // Shared library (view: all auth users, submit: teachers only)
                        .requestMatchers("/api/library/submit").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/api/library/**").authenticated()

                        // Schemas (CONTENT_REVIEWER + ADMIN manage, others view approved only)
                        .requestMatchers("/api/schemas/create", "/api/schemas/*/update").hasAnyRole("CONTENT_REVIEWER", "ADMIN")
                        .requestMatchers("/api/schemas/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated())

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
