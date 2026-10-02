package com.example.backend.system.account.service;

import com.example.backend.exception.ApiException;
import com.example.backend.security.JwtUtil;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.school.repository.LicensePlanRepository;
import com.example.backend.system.school.service.LicenseCheckService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountPasswordLifecycleTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);

    private User account() {
        User user = new User(); user.setId(7); user.setEmail("initial@example.test"); user.setFullName("Initial User");
        Role role = new Role(); role.setName("STUDENT"); user.setRole(role);
        user.setPassword(encoder.encode("initial-secret")); user.setMustChangePassword(true);
        user.setAvatarUrl("https://example.test/avatar.png");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user)); return user;
    }

    @Test void initialPasswordIsReplacedByHashAndPendingFlagClearsOnlyAfterSuccess() {
        User user = account(); when(users.save(user)).thenReturn(user);
        var result = new UserService(users, current, encoder, mock(LicenseCheckService.class)).changePassword(" INITIAL@example.test ", "initial-secret", "private-secret");
        assertFalse(user.isMustChangePassword()); assertFalse(result.isMustChangePassword());
        assertTrue(encoder.matches("private-secret", user.getPassword()));
        assertFalse(encoder.matches("initial-secret", user.getPassword()));
        assertNotEquals("private-secret", user.getPassword());
        assertEquals("https://example.test/avatar.png", result.getAvatarUrl());
    }

    @Test void wrongInitialPasswordDoesNotClearRestriction() {
        User user = account(); String before = user.getPassword();
        var error = assertThrows(ApiException.class, () -> new UserService(users, current, encoder, mock(LicenseCheckService.class))
            .changePassword(user.getEmail(), "wrong-password", "private-secret"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(user.isMustChangePassword()); assertEquals(before, user.getPassword());
        verify(users, never()).save(any());
    }

    @Test void sameOrShortPasswordCannotFinishInitialSetup() {
        User user = account(); var service = new UserService(users, current, encoder, mock(LicenseCheckService.class));
        assertThrows(ApiException.class, () -> service.changePassword(user.getEmail(), "initial-secret", "initial-secret"));
        assertThrows(ApiException.class, () -> service.changePassword(user.getEmail(), "initial-secret", "short"));
        assertTrue(user.isMustChangePassword()); verify(users, never()).save(any());
    }

    @Test void initialLoginReturnsPendingUserEvenBeforeSchoolLicenseIsActive() {
        User user = account(); var jwt = mock(JwtUtil.class); var license = mock(LicenseCheckService.class);
        when(jwt.generateToken(user.getEmail(), "STUDENT")).thenReturn("restricted-token");
        var auth = new AuthService(users, jwt, encoder, mock(LicensePlanRepository.class), license);
        var response = auth.login(" INITIAL@example.test ", "initial-secret");
        assertEquals("restricted-token", response.getToken()); assertTrue(response.getUser().isMustChangePassword());
        assertEquals(user.getAvatarUrl(), response.getUser().getAvatarUrl());
        assertNotNull(user.getLastLogin()); verify(users).save(user);
        user.setMustChangePassword(false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> auth.login(user.getEmail(), "initial-secret")).getStatus());
    }

    @Test void legacyRawPasswordIsHashedOnLoginAndRequiresReplacement() {
        User user = account(); user.setPassword("legacy-secret"); user.setMustChangePassword(false);
        var jwt = mock(JwtUtil.class); when(jwt.generateToken(user.getEmail(), "STUDENT")).thenReturn("restricted-token");
        var auth = new AuthService(users, jwt, encoder, mock(LicensePlanRepository.class), mock(LicenseCheckService.class));
        var response = auth.login(user.getEmail(), "legacy-secret");
        assertTrue(response.getUser().isMustChangePassword());
        assertTrue(encoder.matches("legacy-secret", user.getPassword()));
        assertNotEquals("legacy-secret", user.getPassword()); verify(users).save(user);
    }

    @Test void unicodeByteLimitIsValidatedBeforeEncodingAndDoesNotClearFlag() {
        User user = account(); var service = new UserService(users, current, encoder, mock(LicenseCheckService.class));
        String tooManyBytes = "ậ".repeat(25);
        assertEquals(75, tooManyBytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        var error = assertThrows(ApiException.class, () -> service.changePassword(user.getEmail(), "initial-secret", tooManyBytes));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(user.isMustChangePassword()); verify(users, never()).save(any());
        String validBoundary = "ậ".repeat(24);
        when(users.save(user)).thenReturn(user);
        assertFalse(service.changePassword(user.getEmail(), "initial-secret", validBoundary).isMustChangePassword());
        assertTrue(encoder.matches(validBoundary, user.getPassword()));
    }
}
