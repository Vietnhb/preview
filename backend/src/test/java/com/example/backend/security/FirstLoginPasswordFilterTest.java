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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirstLoginPasswordFilterTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private User account(RoleName name) {
        User user = new User(); user.setEmail("initial@example.test"); user.setMustChangePassword(true);
        Role role = new Role(); role.setName(name.name()); user.setRole(role); return user;
    }

    private JwtFilter filter(User user, LicenseCheckService license) {
        var jwt = mock(JwtUtil.class);
        var users = mock(UserRepository.class);
        when(jwt.extractClaims("token")).thenReturn(Jwts.claims().subject(user.getEmail()).build());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return new JwtFilter(jwt, users, license, new ObjectMapper());
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path); request.addHeader("Authorization", "Bearer token"); return request;
    }

    @ParameterizedTest @EnumSource(RoleName.class)
    void everyRoleIsBlockedFromApplicationUntilPasswordChanges(RoleName role) throws Exception {
        User user = account(role); var license = mock(LicenseCheckService.class); var filter = filter(user, license);
        var request = request("GET", "/api/library/community");
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain, license);
    }

    @ParameterizedTest @CsvSource({"GET,/api/user/me", "PUT,/api/user/me/password", "POST,/api/auth/logout"})
    void lifecycleEndpointsRemainAvailableWithExpiredLicense(String method, String path) throws Exception {
        User user = account(RoleName.STUDENT); var license = mock(LicenseCheckService.class); var filter = filter(user, license);
        var request = request(method, path); var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertEquals("initial@example.test", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest @CsvSource({"GET,/api/user/me/license", "PUT,/api/user/me/profile", "PUT,/api/user/me/avatar", "POST,/api/user/me/password", "GET,/api/auth/logout", "GET,/api/user/me/password", "GET,/api/user/me/extra"})
    void allowListUsesExactMethodAndPath(String method, String path) throws Exception {
        User user = account(RoleName.MANAGER); var license = mock(LicenseCheckService.class); var filter = filter(user, license);
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        filter.doFilter(request(method, path), response, chain);
        assertEquals(403, response.getStatus()); verifyNoInteractions(chain, license);
    }

    @Test void currentDatabaseFlagImmediatelyRestrictsAnAlreadyIssuedTokenAndClearsAfterChange() throws Exception {
        User user = account(RoleName.MANAGER); user.setMustChangePassword(false);
        var license = mock(LicenseCheckService.class); when(license.isLicenseActive(user)).thenReturn(true);
        var filter = filter(user, license); var chain = mock(FilterChain.class);
        var first = request("GET", "/api/admin/users"); var firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, chain); verify(chain).doFilter(first, firstResponse);
        user.setMustChangePassword(true);
        var resetResponse = new MockHttpServletResponse(); filter.doFilter(request("GET", "/api/admin/users"), resetResponse, chain);
        assertEquals(403, resetResponse.getStatus());
        user.setMustChangePassword(false);
        var changed = request("GET", "/api/admin/users"); var changedResponse = new MockHttpServletResponse();
        filter.doFilter(changed, changedResponse, chain); verify(chain).doFilter(changed, changedResponse);
        assertEquals(200, changedResponse.getStatus());
    }
}
