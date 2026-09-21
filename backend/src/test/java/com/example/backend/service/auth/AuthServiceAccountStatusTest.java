package com.example.backend.service.auth;

import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.LicensePlanRepository;
import com.example.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceAccountStatusTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final LicensePlanRepository plans = mock(LicensePlanRepository.class);
    private final AuthService service = new AuthService(users, jwt, passwords, plans);

    @Test
    void nullActiveIsReportedAsLocked() {
        assertLocked(null);
    }

    @Test
    void falseActiveIsReportedAsLocked() {
        assertLocked(false);
    }

    private void assertLocked(Boolean active) {
        User user = new User();
        user.setEmail("locked@example.test");
        user.setActive(active);
        when(users.findByEmail("locked@example.test")).thenReturn(Optional.of(user));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.login("locked@example.test", "password"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên.", exception.getMessage());
    }
}
