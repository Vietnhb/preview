package com.example.backend.security;

import com.example.backend.config.properties.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.FilterChain;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigAuthorizationTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityConfigAuthorizationTest.PropertiesConfiguration.class,
        SecurityConfigAuthorizationTest.ProbeController.class})
class SecurityConfigAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProbeController probe;

    @MockBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void evaluationsRequireReviewerOrAdmin() throws Exception {
        mockMvc.perform(post("/api/evaluations/run"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/evaluations/run")
                        .with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/evaluations/run")
                        .with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk());
        assertThat(probe.evaluationCalls()).isEqualTo(1);
    }

    @Test
    void assignmentCreationIsTeacherOrAdminOnly() throws Exception {
        mockMvc.perform(post("/api/assignments")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/assignments")
                        .with(user("teacher").roles("TEACHER")))
                .andExpect(status().isOk());
        assertThat(probe.assignmentCalls()).isEqualTo(1);
    }

    @Test
    void supportAdminListIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/support/admin")
                        .with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/support/admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        assertThat(probe.supportCalls()).isEqualTo(1);
    }

    @RestController
    static class ProbeController {
        private final AtomicInteger evaluationCalls = new AtomicInteger();
        private final AtomicInteger assignmentCalls = new AtomicInteger();
        private final AtomicInteger supportCalls = new AtomicInteger();

        @PostMapping("/api/evaluations/run")
        void evaluation() {
            evaluationCalls.incrementAndGet();
        }

        @PostMapping("/api/assignments")
        void assignment() {
            assignmentCalls.incrementAndGet();
        }

        @GetMapping("/api/support/admin")
        void support() {
            supportCalls.incrementAndGet();
        }

        int evaluationCalls() {
            return evaluationCalls.get();
        }

        int assignmentCalls() {
            return assignmentCalls.get();
        }

        int supportCalls() {
            return supportCalls.get();
        }
    }

    @TestConfiguration
    static class PropertiesConfiguration {
        @Bean
        SecurityProperties securityProperties() {
            return new SecurityProperties(List.of("http://localhost"), 10);
        }
    }
}
