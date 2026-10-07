package com.example.backend.security;

import com.example.backend.config.SecurityConfig;
import com.example.backend.config.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig(RoleAuthorizationTest.Config.class)
class RoleAuthorizationTest {
    MockMvc mvc;

    @BeforeEach void setup(WebApplicationContext context) {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @CsvSource({
        "ADMIN,GET,/api/admin/users,200",
        "ADMIN,POST,/api/admin/users,200", "ADMIN,PUT,/api/admin/users/7,200",
        "ADMIN,PUT,/api/admin/users/7/suspend,200", "ADMIN,GET,/api/admin/schools,403",
        "ADMIN,POST,/api/admin/users/7/reset-password,200", "ADMIN,PUT,/api/admin/users/7/restore,200",
        "ADMIN,POST,/api/reviewer/solvers,403", "ADMIN,POST,/api/simulation,403",
        "ADMIN,GET,/api/library,403", "ADMIN,GET,/api/exports/123/csv,403",
        "ADMIN,GET,/api/user/me,200", "ADMIN,PUT,/api/user/me/password,200",
        "MANAGER,GET,/api/admin/users,200", "MANAGER,POST,/api/admin/users,200",
        "MANAGER,PUT,/api/admin/users/7,200", "MANAGER,GET,/api/admin/schools,200",
        "MANAGER,POST,/api/admin/users/7/reset-password,200",
        "MANAGER,POST,/api/reviewer/solvers,200", "MANAGER,POST,/api/simulation,200",
        "REVIEWER,POST,/api/reviewer/solvers,200", "REVIEWER,GET,/api/admin/users,403",
        "REVIEWER,POST,/api/simulation,403", "REVIEWER,GET,/api/library,200",
        "SCHOOL,GET,/api/schools/123/users,200", "SCHOOL,GET,/api/admin/users,403",
        "SCHOOL,POST,/api/schools/123/users/7/reset-password,200", "SCHOOL,POST,/api/admin/users/7/reset-password,403",
        "SCHOOL,POST,/api/simulation,403", "SCHOOL,POST,/api/school/billing/checkout,200",
        "STAFF,POST,/api/simulation,200", "STAFF,POST,/api/assignments,200",
        "STAFF,POST,/api/reviewer/solvers,403", "STAFF,GET,/api/admin/users,403",
        "STAFF,GET,/api/schools/123/users,200", "STAFF,POST,/api/schools/123/users/7/reset-password,403",
        "STUDENT,POST,/api/assignments/123/predictions,200", "STUDENT,POST,/api/simulation,403",
        "STUDENT,POST,/api/assignments,403", "STUDENT,GET,/api/library,200",
        "STUDENT,POST,/api/library/123/comments,200", "STUDENT,PUT,/api/library/123/reaction,200",
        "STUDENT,DELETE,/api/library/123/comments/456,200", "STUDENT,DELETE,/api/library/123,403",
        "SCHOOL,POST,/api/library/123/comments,200", "REVIEWER,DELETE,/api/library/123/comments/456,200",
        "ADMIN,POST,/api/library/123/comments,403", "ADMIN,PUT,/api/library/123/reaction,403",
        "TEACHER,POST,/api/simulation,403", "SCHOOL_MANAGER,GET,/api/schools/123/users,403",
        "CONTENT_REVIEWER,POST,/api/reviewer/solvers,403"
    })
    void roleMatrix(String role, String method, String path, int expected) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).with(user("account").roles(role)))
            .andExpect(status().is(expected));
    }

    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfig.class)
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean SecurityProperties properties() { return new SecurityProperties(List.of("http://localhost:5173"), 10); }
        @Bean JwtFilter jwtFilter() throws Exception {
            JwtFilter filter = mock(JwtFilter.class);
            doAnswer(call -> {
                call.getArgument(2, FilterChain.class).doFilter(call.getArgument(0, ServletRequest.class), call.getArgument(1, ServletResponse.class));
                return null;
            }).when(filter).doFilter(any(), any(), any());
            return filter;
        }
        @Bean ProbeController probe() { return new ProbeController(); }
    }

    @ParameterizedTest
    @CsvSource({
        "GET,/api/library/community,200", "GET,/api/library/123/discussion,200", "GET,/api/simulations/shared/123,200", "GET,/api/curriculum,200",
        "POST,/api/library/123/comments,401", "PUT,/api/library/123/reaction,401", "DELETE,/api/library/123/comments/456,401",
        "POST,/api/library/community,401", "GET,/api/library,401",
        "GET,/api/reviewer/library,401", "GET,/api/admin/users,401"
    })
    void anonymousCommunityAccessDoesNotOpenPrivateEndpoints(String method, String path, int expected) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().is(expected));
    }

    @Test
    void authenticationAndPermissionFailuresKeepTheSameJsonContract() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/library"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.message").value("Vui lòng đăng nhập để tiếp tục"));
        mvc.perform(request(HttpMethod.GET, "/api/admin/users").with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này"));
    }

    @RestController static class ProbeController {
        @RequestMapping("/api/**") String ok() { return "ok"; }
    }
}
