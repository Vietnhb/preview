package com.example.backend.security;

import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.service.school.LicenseCheckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtRoleMigrationTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @EnumSource(RoleName.class)
    void currentDatabaseRoleOverridesOldTokenClaim(RoleName currentRole) throws Exception {
        var jwt = mock(JwtUtil.class);
        var users = mock(UserRepository.class);
        var license = mock(LicenseCheckService.class);
        var claims = Jwts.claims().subject("account@example.test").add("role", "ADMIN").build();
        when(jwt.extractClaims("old-token")).thenReturn(claims);
        Role role = new Role(); role.setName(currentRole.name()); role.setId(currentRole.id());
        User user = new User(); user.setRole(role); user.setActive(true);
        when(users.findByEmail("account@example.test")).thenReturn(Optional.of(user));
        when(license.isLicenseActive(user)).thenReturn(true);
        var request = new MockHttpServletRequest("GET", "/api/user/me");
        request.addHeader("Authorization", "Bearer old-token");
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        new JwtFilter(jwt, users, license, new ObjectMapper()).doFilter(request, response, chain);
        assertEquals(currentRole.authority(), SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities().iterator().next().getAuthority());
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest @ValueSource(strings = {"TEACHER", "SCHOOL_MANAGER", "CONTENT_REVIEWER"})
    void obsoleteDatabaseRoleDoesNotAuthenticate(String name) throws Exception {
        var jwt = mock(JwtUtil.class);
        var users = mock(UserRepository.class);
        var license = mock(LicenseCheckService.class);
        when(jwt.extractClaims("token")).thenReturn(Jwts.claims().subject("account@example.test").build());
        Role role = new Role(); role.setName(name);
        User user = new User(); user.setRole(role); user.setActive(true);
        when(users.findByEmail("account@example.test")).thenReturn(Optional.of(user));
        var request = new MockHttpServletRequest("GET", "/api/user/me");
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new JwtFilter(jwt, users, license, new ObjectMapper()).doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain, license);
    }
}
