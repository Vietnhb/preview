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
 * - ADMIN: Views users and creates managers only
 * - MANAGER: Platform admin, manages all schools
 * - REVIEWER: Reviews shared simulations
 * - SCHOOL: Manages one school (users, classes)
 * - STAFF: Creates simulations, teaches classes
 * - STUDENT: Takes classes, submits work
 */
@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {
        private static final String ADMIN = "ADMIN";
        private static final String MANAGER = "MANAGER";
        private static final String REVIEWER = "REVIEWER";
        private static final String SCHOOL = "SCHOOL";
        private static final String STAFF = "STAFF";
        private static final String STUDENT = "STUDENT";
        private static final String[] APPLICATION_ROLES = { MANAGER, REVIEWER, SCHOOL, STAFF, STUDENT };
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
                                                .requestMatchers(HttpMethod.GET, "/api/library/community", "/api/simulations/shared/*", "/api/curriculum").permitAll()
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                                .requestMatchers("/api/auth/**", "/ws/**", "/actuator/health")
                                                .permitAll()

                                                // User profile (all authenticated users)
                                                .requestMatchers("/api/user/me", "/api/user/me/**", "/api/user/profile/**")
                                                .hasAnyRole(ADMIN, MANAGER, REVIEWER, SCHOOL, STAFF, STUDENT)

                                                // ADMIN may inspect users and create MANAGER only (service validates target role).
                                                .requestMatchers(HttpMethod.GET, "/api/admin/users", "/api/user/all")
                                                .hasAnyRole(ADMIN, MANAGER)
                                                .requestMatchers(HttpMethod.POST, "/api/admin/users")
                                                .hasAnyRole(ADMIN, MANAGER)
                                                .requestMatchers(HttpMethod.PUT, "/api/admin/users/**")
                                                .hasAnyRole(ADMIN, MANAGER)
                                                .requestMatchers(HttpMethod.POST, "/api/admin/users/*/reset-password")
                                                .hasAnyRole(ADMIN, MANAGER)

                                                // Platform management (MANAGER)
                                                .requestMatchers("/api/admin/**").hasRole(MANAGER)
                                                .requestMatchers("/api/user/all").hasRole(MANAGER)
                                                .requestMatchers("/api/user/students").hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers("/api/schools/*/activate", "/api/schools/*/deactivate")
                                                .hasRole(MANAGER)

                                                // Content Reviewer (platform-level content moderation)
                                                .requestMatchers("/api/reviewer/**").hasAnyRole(REVIEWER, MANAGER)
                                                .requestMatchers("/api/simulations/*/approve",
                                                                "/api/simulations/*/reject")
                                                .hasAnyRole(REVIEWER, MANAGER)

                                                // SCHOOL (school-level management)
                                                .requestMatchers(HttpMethod.GET, "/api/schools/*/users")
                                                .hasAnyRole(SCHOOL, MANAGER, STAFF)
                                                .requestMatchers("/api/schools/*/users/**")
                                                .hasAnyRole(SCHOOL, MANAGER)
                                                .requestMatchers("/api/schools/*/classes/**")
                                                .hasAnyRole(SCHOOL, MANAGER, STAFF)
                                                .requestMatchers("/api/schools/*/library/**", "/api/schools/*/assignments", "/api/schools/*/imports/**")
                                                .hasAnyRole(SCHOOL, MANAGER, STAFF)
                                                .requestMatchers("/api/schools/*/reports/**")
                                                .hasAnyRole(SCHOOL, MANAGER)

                                                .requestMatchers("/api/problems/**", "/api/simulation/**")
                                                .hasAnyRole(STAFF, MANAGER)

                                                // Reviewer evaluation runs are platform-level operations. Keep this
                                                // before the authenticated fallback so school users cannot start or
                                                // inspect corpus evaluations.
                                                .requestMatchers("/api/evaluations", "/api/evaluations/**")
                                                .hasAnyRole(REVIEWER, MANAGER)

                                                // Teacher (content creation + teaching)
                                                .requestMatchers(HttpMethod.POST, "/api/simulations",
                                                                "/api/simulations/adjust", "/api/simulations/create")
                                                .hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers("/api/classes/*/students")
                                                .hasAnyRole(STAFF, SCHOOL, MANAGER)

                                                // Student (learning + submissions)
                                                .requestMatchers("/api/student/**").hasRole(STUDENT)
                                                .requestMatchers("/api/support/admin", "/api/support/admin/**")
                                                .hasRole(MANAGER)
                                                .requestMatchers("/api/support/**").hasAnyRole(APPLICATION_ROLES)

                                                // School billing
                                                .requestMatchers("/api/school/billing", "/api/school/billing/**")
                                                .hasRole(SCHOOL)

                                                // Assignments: keep teacher and student operations separate.
                                                .requestMatchers("/api/assignments/mine/teacher",
                                                                "/api/assignments/mine/teacher/classes",
                                                                "/api/assignments/*/submissions",
                                                                "/api/assignments/*/report",
                                                                "/api/assignments/*/submissions/*/grade",
                                                                "/api/assignments/*/submissions/*/reopen")
                                                .hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers("/api/assignments/mine/student",
                                                                "/api/assignments/*/predictions",
                                                                "/api/assignments/*/submit",
                                                                "/api/assignments/*/simulation",
                                                                "/api/assignments/*/simulation/adjust")
                                                .hasRole(STUDENT)
                                                .requestMatchers(HttpMethod.POST, "/api/assignments")
                                                .hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers("/api/assignments", "/api/assignments/**")
                                                .hasAnyRole(STAFF, STUDENT, MANAGER)

                                                // Shared library (view: all auth users, submit: teachers only)
                                                .requestMatchers("/api/library/folders", "/api/library/folders/**")
                                                .hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers(HttpMethod.POST, LIBRARY, LIBRARY_ALL)
                                                .hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers(HttpMethod.PATCH, LIBRARY, LIBRARY_ALL)
                                                .hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers(HttpMethod.DELETE, LIBRARY, LIBRARY_ALL)
                                                .hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers("/api/library/mine").hasAnyRole(STAFF, MANAGER)
                                                .requestMatchers(LIBRARY, LIBRARY_ALL).hasAnyRole(APPLICATION_ROLES)

                                                // Schemas (REVIEWER + MANAGER manage, others view approved only)
                                                .requestMatchers(HttpMethod.POST, SCHEMAS, SCHEMAS_ALL)
                                                .hasAnyRole(REVIEWER, MANAGER)
                                                .requestMatchers(HttpMethod.PUT, SCHEMAS, SCHEMAS_ALL)
                                                .hasAnyRole(REVIEWER, MANAGER)
                                                .requestMatchers(SCHEMAS, SCHEMAS_ALL).hasAnyRole(APPLICATION_ROLES)

                                                // All other requests require authentication
                                                .anyRequest().hasAnyRole(APPLICATION_ROLES))

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
