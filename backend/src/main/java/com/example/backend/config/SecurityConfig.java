package com.example.backend.config;

import com.example.backend.base.web.dto.ErrorResponse;
import com.example.backend.security.JwtFilter;
import com.example.backend.system.school.model.entity.School;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.http.HttpMethod.*;

/** HTTP role gates; services and the capability interceptor enforce account and school boundaries. */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String REVIEWER = "REVIEWER";
    private static final String SCHOOL = "SCHOOL";
    private static final String STAFF = "STAFF";
    private static final String STUDENT = "STUDENT";
    private static final String[] APPLICATION_ROLES = {MANAGER, REVIEWER, SCHOOL, STAFF, STUDENT};
    private static final String LIBRARY = "/api/library", LIBRARY_ALL = "/api/library/**";
    private static final String SCHEMAS = "/api/schemas", SCHEMAS_ALL = "/api/schemas/**";
    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) ->
                    writeError(response, 401, "Authentication is required"))
                .accessDeniedHandler((request, response, error) ->
                    writeError(response, 403, "You do not have permission for this action")))
            .authorizeHttpRequests(auth -> auth
                // Anonymous read access is restricted further by publication scope in the services.
                .requestMatchers(GET, "/api/library/community", "/api/library/*/discussion",
                    "/api/simulations/shared/*", "/api/curriculum").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api/auth/**", "/ws/**", "/actuator/health").permitAll()
                .requestMatchers("/api/user/me", "/api/user/me/**").hasAnyRole(ADMIN, MANAGER, REVIEWER, SCHOOL, STAFF, STUDENT)
                // ADMIN manages only MANAGER accounts; target-role restrictions remain in the service.
                .requestMatchers(GET, "/api/admin/users").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers(POST, "/api/admin/users").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers(PUT, "/api/admin/users/**").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers(POST, "/api/admin/users/*/reset-password").hasAnyRole(ADMIN, MANAGER)
                .requestMatchers("/api/admin/**").hasRole(MANAGER)
                .requestMatchers("/api/user/students").hasAnyRole(STAFF, MANAGER)
                .requestMatchers("/api/schools/*/activate", "/api/schools/*/deactivate").hasRole(MANAGER)
                .requestMatchers("/api/reviewer/**").hasAnyRole(REVIEWER, MANAGER)
                // School services validate membership, department-head permissions and class creation.
                .requestMatchers(GET, "/api/schools/*/users").hasAnyRole(SCHOOL, MANAGER, STAFF)
                .requestMatchers("/api/schools/*/users/**").hasAnyRole(SCHOOL, MANAGER)
                .requestMatchers("/api/schools/*/classes/**").hasAnyRole(SCHOOL, MANAGER, STAFF)
                .requestMatchers("/api/schools/*/library/**", "/api/schools/*/assignments", "/api/schools/*/imports/**")
                    .hasAnyRole(SCHOOL, MANAGER, STAFF)
                .requestMatchers("/api/schools/*/reports/**").hasAnyRole(SCHOOL, MANAGER)
                .requestMatchers("/api/problems/**", "/api/simulation/**").hasAnyRole(STAFF, MANAGER)
                .requestMatchers("/api/evaluations", "/api/evaluations/**").hasAnyRole(REVIEWER, MANAGER)
                .requestMatchers("/api/classes/*/students").hasAnyRole(STAFF, SCHOOL, MANAGER)
                .requestMatchers("/api/student/**").hasRole(STUDENT)
                .requestMatchers("/api/support/admin", "/api/support/admin/**").hasRole(MANAGER)
                .requestMatchers("/api/support/**").hasAnyRole(APPLICATION_ROLES)
                .requestMatchers("/api/school/billing", "/api/school/billing/**").hasRole(SCHOOL)
                // Match assignment actions before the broader shared reads.
                .requestMatchers("/api/assignments/mine/teacher", "/api/assignments/mine/teacher/classes",
                    "/api/assignments/*/submissions", "/api/assignments/*/report",
                    "/api/assignments/*/submissions/*/grade", "/api/assignments/*/submissions/*/reopen").hasAnyRole(STAFF, MANAGER)
                .requestMatchers("/api/assignments/mine/student", "/api/assignments/*/predictions",
                    "/api/assignments/*/submit", "/api/assignments/*/simulation", "/api/assignments/*/simulation/adjust").hasRole(STUDENT)
                .requestMatchers(POST, "/api/assignments").hasAnyRole(STAFF, MANAGER)
                .requestMatchers("/api/assignments", "/api/assignments/**").hasAnyRole(STAFF, STUDENT, MANAGER)
                // Community interaction must precede library content-management rules.
                .requestMatchers(POST, "/api/library/*/comments").hasAnyRole(APPLICATION_ROLES)
                .requestMatchers(DELETE, "/api/library/*/comments/*").hasAnyRole(APPLICATION_ROLES)
                .requestMatchers(PUT, "/api/library/*/reaction").hasAnyRole(APPLICATION_ROLES)
                .requestMatchers("/api/library/folders", "/api/library/folders/**").hasAnyRole(STAFF, MANAGER)
                .requestMatchers(POST, LIBRARY, LIBRARY_ALL).hasAnyRole(STAFF, MANAGER)
                .requestMatchers(PATCH, LIBRARY, LIBRARY_ALL).hasAnyRole(STAFF, MANAGER)
                .requestMatchers(DELETE, LIBRARY, LIBRARY_ALL).hasAnyRole(STAFF, MANAGER)
                .requestMatchers("/api/library/mine").hasAnyRole(STAFF, MANAGER)
                .requestMatchers(LIBRARY, LIBRARY_ALL).hasAnyRole(APPLICATION_ROLES)
                .requestMatchers(POST, SCHEMAS, SCHEMAS_ALL).hasAnyRole(REVIEWER, MANAGER)
                .requestMatchers(PUT, SCHEMAS, SCHEMAS_ALL).hasAnyRole(REVIEWER, MANAGER)
                .requestMatchers(SCHEMAS, SCHEMAS_ALL).hasAnyRole(APPLICATION_ROLES)
                .anyRequest().hasAnyRole(APPLICATION_ROLES))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(status, message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(securityProperties.passwordStrength());
    }
}
