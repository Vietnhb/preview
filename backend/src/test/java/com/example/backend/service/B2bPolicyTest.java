package com.example.backend.service;

import com.example.backend.entity.Role;
import com.example.backend.entity.School;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.SchoolRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.dto.admin.CreateManagedUserRequest;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.TopicRepository;
import com.example.backend.repository.SimulationRunRepository;
import com.example.backend.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class B2bPolicyTest {
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LicenseCheckService licenses = new LicenseCheckService(schools);
    private final SchoolService service = new SchoolService(schools, current, licenses);

    private School school() {
        School school = new School();
        school.setId(UUID.randomUUID());
        school.setLicenseStart(LocalDate.now().minusDays(1));
        school.setLicenseEnd(LocalDate.now().plusDays(1));
        school.setMonthlyTokenQuota(100);
        school.setTokenUsageMonth(LocalDate.now().withDayOfMonth(1));
        return school;
    }
    private User user(String roleName, School school) {
        Role role = new Role(); role.setName(roleName);
        User user = new User(); user.setId(1); user.setRole(role); user.setSchool(school);
        return user;
    }
    private void actor(School school) {
        when(current.requireCurrentUser()).thenReturn(user("TEACHER", school));
        when(schools.findByIdForUpdate(school.getId())).thenReturn(Optional.of(school));
    }
    private com.fasterxml.jackson.databind.JsonNode response(int tokens) {
        var response = new ObjectMapper().createObjectNode();
        response.putObject("usage").put("total_tokens", tokens);
        return response;
    }
    @Test void rejectsMissingUsersAndSchoollessTeachers() {
        assertFalse(licenses.canPerformWriteOperations(null));
        assertFalse(licenses.canPerformWriteOperations(user("TEACHER", null)));
        assertTrue(licenses.canPerformWriteOperations(user("ADMIN", null)));
    }
    @Test void inactiveSchoolAndUserCannotWrite() {
        School school = school(); User teacher = user("TEACHER", school);
        school.setActive(false); assertFalse(licenses.canPerformWriteOperations(teacher));
        school.setActive(true); teacher.setActive(false);
        assertFalse(licenses.canPerformWriteOperations(teacher));
    }
    @Test void licenseIsInclusiveAndFutureIsNotGraceMode() {
        School school = school(); User teacher = user("TEACHER", school);
        school.setLicenseStart(LocalDate.now()); school.setLicenseEnd(LocalDate.now());
        assertTrue(licenses.canPerformWriteOperations(teacher));
        school.setLicenseStart(LocalDate.now().plusDays(1)); school.setLicenseEnd(LocalDate.now().plusDays(2));
        assertFalse(licenses.canPerformWriteOperations(teacher));
        assertFalse(licenses.isInGraceMode(teacher));
    }
    @Test void expiredLicenseIsReadOnlyAndMissingDatesDoNotCrash() {
        School school = school(); User teacher = user("TEACHER", school);
        school.setLicenseEnd(LocalDate.now().minusDays(1));
        assertTrue(licenses.isInGraceMode(teacher));
        assertFalse(licenses.canPerformWriteOperations(teacher));
        school.setLicenseEnd(null);
        assertFalse(licenses.canPerformWriteOperations(teacher));
        assertDoesNotThrow(() -> licenses.status(teacher));
    }
    @Test void countsActualTokensAndBlocksAfterFinishingOverQuota() {
        School school = school(); school.setUsedTokens(90L); actor(school);
        service.meterAiCall(() -> response(25));
        assertEquals(115L, school.getUsedTokens());
        assertThrows(ApiException.class, () -> service.meterAiCall(() -> { fail("Must not call provider"); return null; }));
    }
    @Test void resetsMonthlyUsageBeforeCheckingQuota() {
        School school = school(); school.setUsedTokens(500L);
        school.setTokenUsageMonth(LocalDate.now().minusMonths(1).withDayOfMonth(1)); actor(school);
        service.meterAiCall(() -> response(12));
        assertEquals(12L, school.getUsedTokens());
        assertEquals(LocalDate.now().withDayOfMonth(1), school.getTokenUsageMonth());
    }
    @Test void unlimitedQuotaStillRecordsUsage() {
        School school = school(); school.setMonthlyTokenQuota(null); actor(school);
        service.meterAiCall(() -> response(150)); assertEquals(150L, school.getUsedTokens());
    }
    @Test void rejectsMissingUsageInsteadOfInventingTokenCounts() {
        School school = school(); actor(school);
        assertThrows(ApiException.class, () -> service.meterAiCall(() -> new ObjectMapper().createObjectNode()));
        assertEquals(0L, school.getUsedTokens());
        verify(schools, never()).save(any());
    }
    @Test void failedProviderCallDoesNotInventUsage() {
        School school = school(); actor(school);
        assertThrows(IllegalStateException.class, () -> service.meterAiCall(() -> { throw new IllegalStateException(); }));
        assertEquals(0L, school.getUsedTokens());
    }
    @Test void reviewerCannotSpendSchoolQuota() {
        School school = school(); when(current.requireCurrentUser()).thenReturn(user("CONTENT_REVIEWER", school));
        assertThrows(ApiException.class, () -> service.meterAiCall(() -> { fail("Forbidden"); return null; }));
    }
    @Test void schoolManagerCannotManageAnotherSchool() {
        User manager = user("SCHOOL_MANAGER", school());
        RoleValidationService roles = new RoleValidationService(mock(UserRepository.class));
        assertTrue(roles.canManageSchool(manager, manager.getSchool().getId()));
        assertFalse(roles.canManageSchool(manager, UUID.randomUUID()));
        manager.setActive(false);
        assertFalse(roles.canManageSchool(manager, manager.getSchool().getId()));
    }
    @Test void publicSignupCannotCreateAccounts() {
        UserRepository users = mock(UserRepository.class);
        var auth = new AuthService(users, mock(JwtUtil.class), mock(PasswordEncoder.class));
        assertThrows(ApiException.class, () -> auth.signup(null));
        verifyNoInteractions(users);
    }
    @Test void schoolManagerCannotEscalateRoleOrCreateInAnotherSchool() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        School own = school(); School other = school();
        when(current.requireCurrentUser()).thenReturn(user("SCHOOL_MANAGER", own));
        when(schools.findById(other.getId())).thenReturn(Optional.of(other));
        Role teacherRole = new Role(); teacherRole.setName("TEACHER");
        Role adminRole = new Role(); adminRole.setName("ADMIN");
        when(roles.findByName("TEACHER")).thenReturn(Optional.of(teacherRole));
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        var admin = new AdminService(users, roles, mock(TopicRepository.class), mock(SimulationRunRepository.class),
                mock(PasswordEncoder.class), current, new RoleValidationService(users), licenses, schools, mock(jakarta.persistence.EntityManager.class));
        assertThrows(ApiException.class, () -> admin.createUser(new CreateManagedUserRequest(
                "teacher@example.com", "password123", "Teacher", "TEACHER", other.getId().toString())));
        assertThrows(ApiException.class, () -> admin.createUser(new CreateManagedUserRequest(
                "admin@example.com", "password123", "Admin", "ADMIN", null)));
        verify(users, never()).save(any());
    }
    @Test void sqlInitializerKeepsPostgresFunctionBodiesTogether() throws Exception {
        var connection = mock(java.sql.Connection.class);
        var statement = mock(java.sql.Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                new org.springframework.core.io.support.EncodedResource(new org.springframework.core.io.ClassPathResource("data.sql")),
                false, false, "--", ";;", "/*", "*/");
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sql.capture());
        assertTrue(sql.getAllValues().stream().anyMatch(query -> query.contains("RETURNS trigger")
                && query.contains("RAISE EXCEPTION") && query.contains("END $$")));
    }

}
