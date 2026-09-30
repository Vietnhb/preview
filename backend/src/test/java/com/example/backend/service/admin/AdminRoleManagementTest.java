package com.example.backend.service.admin;

import com.example.backend.dto.admin.CreateManagedUserRequest;
import com.example.backend.dto.admin.UpdateManagedUserRequest;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.account.RoleRepository;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.curriculum.TopicRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.service.account.RoleValidationService;
import com.example.backend.service.school.LicenseCheckService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRoleManagementTest {
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock TopicRepository topicRepository;
    @Mock SimulationRunRepository simulationRunRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CurrentUserService currentUserService;
    @Mock RoleValidationService roleValidationService;
    @Mock LicenseCheckService licenseCheckService;
    @Mock SchoolRepository schoolRepository;
    @Mock EntityManager entityManager;
    @Spy AccountAccessService accountAccessService = new AccountAccessService();
    @InjectMocks AdminService service;

    private Role role(String name) {
        Role role = new Role(); role.setName(name); role.setId(RoleName.valueOf(name).id()); return role;
    }
    private User account(int id, String name) {
        User user = new User(); user.setId(id); user.setRole(role(name)); user.setFullName("Test"); return user;
    }

    @ParameterizedTest @CsvSource({"ADMIN,MANAGER", "MANAGER,REVIEWER"})
    void allowedCreation(String actor, String target) {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, actor));
        when(roleRepository.findByName(target)).thenReturn(Optional.of(role(target)));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(call -> { User user = call.getArgument(0); user.setId(9); return user; });
        var result = service.createUser(new CreateManagedUserRequest("new@example.test", "password123", "New User", target, null));
        assertEquals(target, result.role());
        assertTrue(result.mustChangePassword());
        verify(userRepository).save(argThat(user -> "hashed".equals(user.getPassword())
            && user.isMustChangePassword() && user.getSchool() == null));
    }

    @ParameterizedTest @CsvSource({"ADMIN,REVIEWER", "ADMIN,ADMIN", "MANAGER,ADMIN", "MANAGER,MANAGER", "SCHOOL,MANAGER"})
    void forbiddenCreation(String actor, String target) {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, actor));
        when(roleRepository.findByName(target)).thenReturn(Optional.of(role(target)));
        var error = assertThrows(ApiException.class, () -> service.createUser(
            new CreateManagedUserRequest("new@example.test", "password123", "New User", target, null)));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test void managerCannotPromoteExistingReviewerToManager() {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, "MANAGER"));
        when(userRepository.findById(2)).thenReturn(Optional.of(account(2, "REVIEWER")));
        when(roleRepository.findByName("MANAGER")).thenReturn(Optional.of(role("MANAGER")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.updateUser(2, new UpdateManagedUserRequest("Name", "MANAGER", null))).getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test void managerCannotSuspendAdmin() {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, "MANAGER"));
        when(userRepository.findById(2)).thenReturn(Optional.of(account(2, "ADMIN")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.setActive(2, false)).getStatus());
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest @CsvSource({"ADMIN,MANAGER", "MANAGER,MANAGER", "MANAGER,REVIEWER"})
    void allowedPasswordResetStoresHashAndRequiresReplacement(String actor, String target) {
        User existing = account(2, target); existing.setPassword("old-hash");
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, actor));
        when(userRepository.findById(2)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("initial-secret")).thenReturn("replacement-hash");
        when(userRepository.save(existing)).thenReturn(existing);
        var result = service.resetPassword(2, "initial-secret");
        assertTrue(result.mustChangePassword());
        assertEquals("replacement-hash", existing.getPassword());
        assertNotEquals("initial-secret", existing.getPassword());
    }

    @ParameterizedTest @CsvSource({"ADMIN,ADMIN", "ADMIN,REVIEWER", "ADMIN,STAFF", "MANAGER,ADMIN", "SCHOOL,MANAGER", "STUDENT,REVIEWER"})
    void passwordResetCannotBypassRoleScope(String actor, String target) {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, actor));
        when(userRepository.findById(2)).thenReturn(Optional.of(account(2, target)));
        var error = assertThrows(ApiException.class, () -> service.resetPassword(2, "initial-secret"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest @CsvSource({"ADMIN,MANAGER", "MANAGER,MANAGER"})
    void managersCanBeEditedByAdminAndManager(String actor, String target) {
        User existing = account(2, target);
        existing.setAvatarUrl("https://example.test/avatar.jpg");
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, actor));
        when(userRepository.findById(2)).thenReturn(Optional.of(existing));
        when(roleRepository.findByName(target)).thenReturn(Optional.of(role(target)));
        when(userRepository.save(existing)).thenReturn(existing);
        var result = service.updateUser(2, new UpdateManagedUserRequest("Changed", target, null));
        assertEquals("Changed", result.fullName());
        assertEquals("https://example.test/avatar.jpg", result.avatarUrl());
        assertNull(result.staffType());
        assertFalse(result.reviewerCanEdit());
    }

    @Test void adminCannotDemoteManagerToReviewer() {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, "ADMIN"));
        when(userRepository.findById(2)).thenReturn(Optional.of(account(2, "MANAGER")));
        when(roleRepository.findByName("REVIEWER")).thenReturn(Optional.of(role("REVIEWER")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.updateUser(2, new UpdateManagedUserRequest("Name", "REVIEWER", null))).getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test void adminCanSuspendAndRestoreManager() {
        User existing = account(2, "MANAGER");
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, "ADMIN"));
        when(userRepository.findById(2)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        assertFalse(service.setActive(2, false).active());
        assertEquals(1, existing.getDeactivatedBy());
        assertNotNull(existing.getDeactivatedAt());
        assertTrue(service.setActive(2, true).active());
        assertNull(existing.getDeactivatedAt());
    }

    @Test void reviewerCreationHonorsCapabilitiesAndProfileFields() {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, "MANAGER"));
        when(roleRepository.findByName("REVIEWER")).thenReturn(Optional.of(role("REVIEWER")));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        var dob = java.time.LocalDate.of(1992, 6, 12);
        var response = service.createUser(new CreateManagedUserRequest("new@example.test", "password123", "Reviewer",
            "REVIEWER", null, dob, "https://example.test/avatar.png", null, true, false));
        assertEquals(dob, response.dateOfBirth());
        assertEquals("https://example.test/avatar.png", response.avatarUrl());
        assertTrue(response.reviewerCanEdit());
        assertFalse(response.reviewerCanReview());
        assertNull(response.staffType());
    }

    @Test void schoolResetsOnlyOwnStudentsAndTeachers() {
        var school = new com.example.backend.entity.school.School(); school.setId(java.util.UUID.randomUUID());
        User actor = account(1, "SCHOOL"); actor.setSchool(school);
        User existing = account(2, "STAFF"); existing.setSchool(school);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(userRepository.findById(2)).thenReturn(Optional.of(existing));
        when(roleValidationService.canManageSchool(actor, school.getId())).thenReturn(true);
        when(passwordEncoder.encode("initial-secret")).thenReturn("hashed");
        when(userRepository.save(existing)).thenReturn(existing);
        assertTrue(service.resetPassword(2, "initial-secret").mustChangePassword());
        verify(licenseCheckService).requireWriteAccess(actor);
        var otherSchool = new com.example.backend.entity.school.School(); otherSchool.setId(java.util.UUID.randomUUID());
        existing.setSchool(otherSchool);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.resetPassword(2, "another-secret")).getStatus());
        verify(passwordEncoder, times(1)).encode(any());
    }

    @Test void schoolRouteDoesNotAllowUserFromDifferentSchool() {
        var school = new com.example.backend.entity.school.School(); school.setId(java.util.UUID.randomUUID());
        var otherSchool = new com.example.backend.entity.school.School(); otherSchool.setId(java.util.UUID.randomUUID());
        User actor = account(1, "SCHOOL"); actor.setSchool(school);
        User existing = account(2, "STUDENT"); existing.setSchool(otherSchool);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(roleValidationService.canManageSchool(actor, school.getId())).thenReturn(true);
        when(userRepository.findById(2)).thenReturn(Optional.of(existing));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
            () -> service.requireUserInSchool(school.getId(), 2)).getStatus());
    }

    @Test void departmentHeadHasOwnSchoolReadAccessWithoutPasswordResetPermission() {
        var school = new com.example.backend.entity.school.School(); school.setId(java.util.UUID.randomUUID());
        User actor = account(1, "STAFF"); actor.setSchool(school); actor.setStaffType("DEPARTMENT_HEAD");
        User student = account(2, "STUDENT"); student.setSchool(school);
        when(currentUserService.requireCurrentUser()).thenReturn(actor);
        when(userRepository.findBySchoolId(school.getId())).thenReturn(java.util.List.of(student));
        assertEquals(1, service.usersForSchool(school.getId()).size());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.usersForSchool(java.util.UUID.randomUUID())).getStatus());
        when(userRepository.findById(2)).thenReturn(Optional.of(student));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.resetPassword(2, "initial-secret")).getStatus());
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"a", "ậ"})
    void resetRejectsUnsupportedBcryptByteLengthBeforeEncoding(String character) {
        when(currentUserService.requireCurrentUser()).thenReturn(account(1, "ADMIN"));
        when(userRepository.findById(2)).thenReturn(Optional.of(account(2, "MANAGER")));
        String initialPassword = character.repeat(character.equals("a") ? 73 : 25);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
            () -> service.resetPassword(2, initialPassword)).getStatus());
        verifyNoInteractions(passwordEncoder); verify(userRepository, never()).save(any());
    }

    @Test void creationChecksEmailUniquenessWithoutCaseSensitivity() {
        when(userRepository.findFirstByEmailIgnoreCase("Legacy@Example.test")).thenReturn(Optional.of(account(2, "MANAGER")));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
            () -> service.createUser(new CreateManagedUserRequest(" Legacy@Example.test ", "password123", "Name", "MANAGER", null))).getStatus());
        verifyNoInteractions(roleRepository, passwordEncoder); verify(userRepository, never()).save(any());
    }
}
