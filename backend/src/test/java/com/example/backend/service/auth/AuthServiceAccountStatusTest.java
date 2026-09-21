package com.example.backend.service.auth;

import com.example.backend.entity.account.User;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.entity.school.School;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.LicensePlanRepository;
import com.example.backend.security.JwtUtil;
import com.example.backend.service.school.LicenseCheckService;
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
    private final LicenseCheckService licenses = mock(LicenseCheckService.class);
    private final AuthService service = new AuthService(users, jwt, passwords, plans, licenses);

    @Test
    void nullActiveIsReportedAsLocked() {
        assertLocked(null);
    }

    @Test
    void falseActiveIsReportedAsLocked() {
        assertLocked(false);
    }

    @Test
    void managerWithoutLicenseCanLoginAndIsSentToBilling() {
        User manager = schoolUser(RoleName.SCHOOL_MANAGER, "manager@example.test");
        when(users.findByEmail("manager@example.test")).thenReturn(Optional.of(manager));
        when(licenses.isLicenseActive(manager)).thenReturn(false);
        when(jwt.generateToken(manager.getEmail(), RoleName.SCHOOL_MANAGER.name())).thenReturn("token");

        var response = service.login("manager@example.test", "password");

        assertEquals(true, response.getUser().isBillingRequired());
    }

    @Test
    void teacherWithoutLicenseCannotLogin() {
        User teacher = schoolUser(RoleName.TEACHER, "teacher@example.test");
        when(users.findByEmail("teacher@example.test")).thenReturn(Optional.of(teacher));
        when(licenses.isLicenseActive(teacher)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.login("teacher@example.test", "password"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
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

    private static User schoolUser(RoleName roleName, String email) {
        Role role = new Role();
        role.setName(roleName.name());
        School school = new School();
        school.setActive(true);
        User user = new User();
        user.setEmail(email);
        user.setPassword("password");
        user.setFullName("School user");
        user.setActive(true);
        user.setRole(role);
        user.setSchool(school);
        return user;
    }
}
