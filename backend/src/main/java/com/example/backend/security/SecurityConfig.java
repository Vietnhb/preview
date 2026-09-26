package com.example.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.backend.config.properties.SecurityProperties;
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
 * - REVIEWER: Reviews shared simulations
 * - SCHOOL_MANAGER: Manages one school (users, classes)
 * - TEACHER: Creates simulations, teaches classes
 * - STUDENT: Takes classes, submits work
 */
@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {
        private static final String ADMIN = "ADMIN";
        private static final String REVIEWER = "REVIEWER";
        private static final String SCHOOL_MANAGER = "SCHOOL_MANAGER";
        private static final String TEACHER = "TEACHER";
        private static final String STUDENT = "STUDENT";
        private static final String LIBRARY = "/api/library";
        private static final String LIBRARY_ALL = "/api/library/**";
        private static final String SCHEMAS = "/api/schemas";
        private static final String SCHEMAS_ALL = "/api/schemas/**";

        private final JwtFilter jwtFilter;
        private final ObjectMapper objectMapper;
        private final SecurityProperties securityProperties;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                                                (request, response, exception) -> {
                                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                        response.setContentType("application/json");
                                                        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                                                        objectMapper.writeValue(response.getOutputStream(),
                                                                        new ErrorResponse(
                                                                                        HttpServletResponse.SC_UNAUTHORIZED,
                                                                                        "Authentication is required"));
                                                })
                                                .accessDeniedHandler((request, response, exception) -> {
                                                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                                        response.setContentType("application/json");
                                                        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                                                        objectMapper.writeValue(response.getOutputStream(),
                                                                        new ErrorResponse(
                                                                                        HttpServletResponse.SC_FORBIDDEN,
                                                                                        "You do not have permission for this action"));
                                                }))
                                .authorizeHttpRequests(auth -> auth
                                                // Public endpoints
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                                .requestMatchers("/api/auth/**", "/ws/**", "/actuator/health")
                                                .permitAll()

                                                // User profile (all authenticated users)
                                                .requestMatchers("/api/user/me", "/api/user/profile/**").authenticated()

                                                // Platform admin only
                                                .requestMatchers("/api/admin/**").hasRole(ADMIN)
                                                .requestMatchers("/api/user/all").hasRole(ADMIN)
                                                .requestMatchers("/api/user/students").hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers("/api/schools/*/activate", "/api/schools/*/deactivate")
                                                .hasRole(ADMIN)

                                                // Content Reviewer (platform-level content moderation)
                                                .requestMatchers("/api/reviewer/**").hasAnyRole(REVIEWER, ADMIN)
                                                .requestMatchers("/api/simulations/*/approve",
                                                                "/api/simulations/*/reject")
                                                .hasAnyRole(REVIEWER, ADMIN)

                                                // School Manager (school-level management)
                                                .requestMatchers("/api/schools/*/users/**")
                                                .hasAnyRole(SCHOOL_MANAGER, ADMIN)
                                                .requestMatchers("/api/schools/*/classes/**")
                                                .hasAnyRole(SCHOOL_MANAGER, ADMIN)
                                                .requestMatchers("/api/schools/*/reports/**")
                                                .hasAnyRole(SCHOOL_MANAGER, ADMIN)

                                                .requestMatchers("/api/problems/**", "/api/matter-flow/**")
                                                .hasAnyRole(TEACHER, ADMIN)

                                                // Reviewer evaluation runs are platform-level operations. Keep this
                                                // before the authenticated fallback so school users cannot start or
                                                // inspect corpus evaluations.
                                                .requestMatchers("/api/evaluations", "/api/evaluations/**")
                                                .hasAnyRole(REVIEWER, ADMIN)

                                                // Teacher (content creation + teaching)
                                                .requestMatchers(HttpMethod.POST, "/api/simulations",
                                                                "/api/simulations/adjust", "/api/simulations/create")
                                                .hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers("/api/classes/*/students")
                                                .hasAnyRole(TEACHER, SCHOOL_MANAGER, ADMIN)

                                                // Student (learning + submissions)
                                                .requestMatchers("/api/student/**").hasRole(STUDENT)
                                                .requestMatchers("/api/support/admin", "/api/support/admin/**")
                                                .hasRole(ADMIN)
                                                .requestMatchers("/api/support/**").authenticated()

                                                // School billing
                                                .requestMatchers("/api/school/billing", "/api/school/billing/**")
                                                .hasRole(SCHOOL_MANAGER)

                                                // Assignments: keep teacher and student operations separate.
                                                .requestMatchers("/api/assignments/mine/teacher",
                                                                "/api/assignments/mine/teacher/classes",
                                                                "/api/assignments/*/submissions",
                                                                "/api/assignments/*/report",
                                                                "/api/assignments/*/submissions/*/grade",
                                                                "/api/assignments/*/submissions/*/reopen")
                                                .hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers("/api/assignments/mine/student",
                                                                "/api/assignments/*/predictions",
                                                                "/api/assignments/*/submit",
                                                                "/api/assignments/*/simulation",
                                                                "/api/assignments/*/simulation/adjust")
                                                .hasRole(STUDENT)
                                                .requestMatchers(HttpMethod.POST, "/api/assignments")
                                                .hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers("/api/assignments", "/api/assignments/**")
                                                .hasAnyRole(TEACHER, STUDENT, ADMIN)

                                                // Shared library (view: all auth users, submit: teachers only)
                                                .requestMatchers("/api/library/folders", "/api/library/folders/**")
                                                .hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers(HttpMethod.POST, LIBRARY, LIBRARY_ALL)
                                                .hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers(HttpMethod.PATCH, LIBRARY, LIBRARY_ALL)
                                                .hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers(HttpMethod.DELETE, LIBRARY, LIBRARY_ALL)
                                                .hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers("/api/library/mine").hasAnyRole(TEACHER, ADMIN)
                                                .requestMatchers(LIBRARY, LIBRARY_ALL).authenticated()

                                                // Schemas (REVIEWER + ADMIN manage, others view approved only)
                                                .requestMatchers(HttpMethod.POST, SCHEMAS, SCHEMAS_ALL)
                                                .hasAnyRole(REVIEWER, ADMIN)
                                                .requestMatchers(HttpMethod.PUT, SCHEMAS, SCHEMAS_ALL)
                                                .hasAnyRole(REVIEWER, ADMIN)
                                                .requestMatchers(SCHEMAS, SCHEMAS_ALL).authenticated()

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
