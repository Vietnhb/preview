package com.example.backend.service.school;

import com.example.backend.dto.admin.CreateManagedUserRequest;
import com.example.backend.dto.admin.UserStatusResponse;
import com.example.backend.dto.school.SchoolImport;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.school.School;
import com.example.backend.entity.school.SchoolClass;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.*;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.RoleValidationService;
import com.example.backend.service.admin.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolImportServiceTest {
    @Mock SchoolRepository schools;
    @Mock SchoolClassRepository classes;
    @Mock ClassEnrollmentRepository enrollments;
    @Mock ClassTeacherAssignmentRepository assignments;
    @Mock UserRepository users;
    @Mock CurrentUserService currentUser;
    @Mock RoleValidationService roles;
    @Mock AccountAccessService accountAccess;
    @Mock LicenseCheckService license;
    @Mock AdminService admin;
    @Mock SchoolClassService classService;
    @Mock jakarta.persistence.EntityManager entityManager;
    @InjectMocks SchoolImportService service;
    final UUID schoolId = UUID.randomUUID();
    School school;
    User actor;

    @BeforeEach void prepare() {
        school = new School(); school.setId(schoolId); school.setName("Trường kiểm thử");
        actor = user(1, "SCHOOL", "school@example.edu.vn");
        lenient().when(currentUser.requireCurrentUser()).thenReturn(actor);
        lenient().when(roles.canManageSchool(actor, schoolId)).thenReturn(true);
        lenient().when(schools.findById(schoolId)).thenReturn(Optional.of(school));
        lenient().when(schools.findByIdForUpdate(schoolId)).thenReturn(Optional.of(school));
    }
    private User user(int id, String roleName, String email) {
        Role role = new Role(); role.setName(roleName);
        User user = new User(); user.setId(id); user.setRole(role); user.setSchool(school); user.setEmail(email); user.setFullName("Nguyễn Minh An"); user.setDateOfBirth(LocalDate.of(2010, 9, 15)); user.setActive(true);
        return user;
    }
    private SchoolImport.Row row(int index, String email) {
        return new SchoolImport.Row(index, Map.of("fullName", "Nguyễn Minh An", "dateOfBirth", "2010-09-15", "email", email, "initialPassword", "", "role", "STUDENT"));
    }
    private static SchoolImport.Request reviewed(SchoolImport.Preview preview) {
        return new SchoolImport.Request(preview.kind(), preview.rows().stream().map(row -> new SchoolImport.Row(row.row(), row.data())).toList(), preview.previewToken());
    }
    private UserStatusResponse created(int id) { return new UserStatusResponse(id, "new@example.edu.vn", "An", "STUDENT", true, schoolId.toString(), null, LocalDate.of(2010, 9, 15), null, schoolId, school.getName(), true, null, false, false); }

    @Test void duplicateNameAndDobRemainImportableWithDifferentEmailAndVisibleMatch() {
        User existing = user(2, "STUDENT", "existing@example.edu.vn");
        when(users.findBySchoolId(schoolId)).thenReturn(List.of(existing));
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "new@example.edu.vn"))));
        assertTrue(preview.canCommit());
        assertEquals(existing.getEmail(), preview.rows().getFirst().matches().getFirst().email());
        assertFalse(preview.rows().getFirst().warnings().isEmpty());
        assertTrue(preview.rows().getFirst().data().get("initialPassword").length() >= 8);
        verifyNoInteractions(admin, classService);
    }
    @Test void duplicatesWithinFileGlobalEmailsInvalidDobAndQuotaBlockWholeBatch() {
        school.setStudentQuota(1);
        when(users.findFirstByEmailIgnoreCase(anyString())).thenAnswer(invocation -> invocation.getArgument(0).equals("taken@example.edu.vn") ? Optional.of(user(2, "STUDENT", "taken@example.edu.vn")) : Optional.empty());
        var badData = new HashMap<>(row(4, "taken@example.edu.vn").data()); badData.put("dateOfBirth", "2010-02-31");
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "same@example.edu.vn"), row(3, "SAME@example.edu.vn"), new SchoolImport.Row(4, badData))));
        assertEquals(3, preview.invalidRows()); assertNull(preview.previewToken());
        assertTrue(preview.rows().getFirst().errors().contains("Email trùng trong tệp."));
        assertTrue(preview.rows().get(2).errors().contains("Email đã được sử dụng."));
        assertTrue(preview.rows().get(2).errors().stream().anyMatch(error -> error.startsWith("Ngày sinh")));
        verifyNoInteractions(admin, classService);
    }
    @Test void commitRequiresUnmodifiedPreviewAndRevalidatesBeforeAnyWrite() {
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "new@example.edu.vn"))));
        assertThrows(ApiException.class, () -> service.commit(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "new@example.edu.vn")))));
        var edited = new HashMap<>(preview.rows().getFirst().data()); edited.put("email", "edited@example.edu.vn");
        assertThrows(ApiException.class, () -> service.commit(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(new SchoolImport.Row(2, edited)), preview.previewToken())));
        when(users.findFirstByEmailIgnoreCase("new@example.edu.vn")).thenReturn(Optional.of(user(9, "STUDENT", "new@example.edu.vn")));
        assertThrows(ApiException.class, () -> service.commit(schoolId, reviewed(preview)));
        verifyNoInteractions(admin, classService);
    }
    @Test void commitsThroughAccountServiceWithDobGeneratedPasswordAndSchoolScope() {
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "NEW@example.edu.vn"))));
        when(admin.createUser(any())).thenReturn(created(3));
        var result = service.commit(schoolId, reviewed(preview));
        ArgumentCaptor<CreateManagedUserRequest> capture = ArgumentCaptor.forClass(CreateManagedUserRequest.class);
        verify(admin).createUser(capture.capture());
        assertEquals("new@example.edu.vn", capture.getValue().email());
        assertEquals(schoolId.toString(), capture.getValue().institutionId());
        assertEquals(LocalDate.of(2010, 9, 15), capture.getValue().dateOfBirth());
        assertEquals(capture.getValue().password(), result.credentials().getFirst().initialPassword());
        assertEquals(1, result.imported());
    }
    @Test void preservesSuppliedPasswordWhitespaceThroughPreviewCommitAndCredentialExport() {
        var data = new HashMap<>(row(2, "new@example.edu.vn").data());
        String password = "  Abcd1234!  "; data.put("initialPassword", password);
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(new SchoolImport.Row(2, data))));
        assertTrue(preview.canCommit()); assertEquals(password, preview.rows().getFirst().data().get("initialPassword"));
        when(admin.createUser(any())).thenReturn(created(3));
        var result = service.commit(schoolId, reviewed(preview));
        verify(admin).createUser(argThat(request -> password.equals(request.password())));
        assertEquals(password, result.credentials().getFirst().initialPassword());
        data.put("initialPassword", "    ");
        var generated = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(new SchoolImport.Row(3, data))));
        assertTrue(generated.canCommit()); assertFalse(generated.rows().getFirst().data().get("initialPassword").isBlank());
    }
    @Test void previewSignatureCannotBeReusedByAnotherActorOrSchool() {
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "new@example.edu.vn"))));
        actor.setId(5);
        assertThrows(ApiException.class, () -> service.commit(schoolId, reviewed(preview)));
        actor.setId(1);
        School other = new School(); other.setId(UUID.randomUUID());
        when(roles.canManageSchool(actor, other.getId())).thenReturn(true);
        when(schools.findByIdForUpdate(other.getId())).thenReturn(Optional.of(other));
        assertThrows(ApiException.class, () -> service.commit(other.getId(), reviewed(preview)));
        verifyNoInteractions(admin, classService);
    }
    @Test void templatesRoundTripAndStaffSubtypeIsPreservedInAccountCreation() {
        byte[] template = service.template(schoolId, SchoolImport.Kind.USERS);
        var rows = SchoolCsvParser.parse(template, SchoolImportService.columns(SchoolImport.Kind.USERS), List.of("fullName", "dateOfBirth", "email", "initialPassword", "role"));
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, rows));
        assertTrue(preview.canCommit()); assertEquals(2, preview.validRows());
        assertEquals("TEACHER", preview.rows().get(1).data().get("staffType"));
        var data = new HashMap<>(preview.rows().get(1).data()); data.put("staffType", "DEPARTMENT_HEAD");
        var headPreview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(new SchoolImport.Row(2, data))));
        when(admin.createUser(any())).thenReturn(created(3));
        service.commit(schoolId, reviewed(headPreview));
        verify(admin).createUser(argThat(request -> "DEPARTMENT_HEAD".equals(request.staffType()) && "STAFF".equals(request.role())));
    }
    @Test void departmentHeadCanImportEnrollmentWithinOwnSchool() {
        actor.getRole().setName("STAFF");
        when(roles.canManageSchool(actor, schoolId)).thenReturn(false);
        when(accountAccess.isDepartmentHead(actor)).thenReturn(true);
        SchoolClass target = new SchoolClass(); target.setId(UUID.randomUUID()); target.setSchool(school); target.setName("10A1"); target.setSchoolYear("2026-2027");
        when(classes.findBySchoolIdAndIsActiveTrue(schoolId)).thenReturn(List.of(target));
        User student = user(8, "STUDENT", "student@example.edu.vn");
        when(users.findFirstByEmailIgnoreCase(student.getEmail())).thenReturn(Optional.of(student));
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.ENROLLMENTS, List.of(new SchoolImport.Row(2, Map.of("studentEmail", student.getEmail(), "classCode", "10A1", "schoolYear", "2026-2027")))));
        assertTrue(preview.canCommit());
        service.commit(schoolId, reviewed(preview));
        verify(classService).enrollStudent(schoolId, target.getId(), student.getId()); verifyNoInteractions(admin);
    }
    @Test void refreshedSchoolQuotaBlocksCommitWhenQuotaChangedAfterPreview() {
        school.setStudentQuota(5);
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "new@example.edu.vn"))));
        assertTrue(preview.canCommit());
        doAnswer(invocation -> { school.setStudentQuota(0); return null; }).when(entityManager).refresh(school, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        assertThrows(ApiException.class, () -> service.commit(schoolId, reviewed(preview)));
        verifyNoInteractions(admin, classService);
    }
    @Test void archivedClassBetweenPreviewAndCommitIsRejectedBeforeEnrollmentWrites() {
        SchoolClass target = new SchoolClass(); target.setId(UUID.randomUUID()); target.setSchool(school); target.setName("10A1"); target.setSchoolYear("2026-2027"); target.setIsActive(true);
        when(classes.findBySchoolIdAndIsActiveTrue(schoolId)).thenReturn(List.of(target));
        User student = user(8, "STUDENT", "student@example.edu.vn");
        when(users.findFirstByEmailIgnoreCase(student.getEmail())).thenReturn(Optional.of(student));
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.ENROLLMENTS, List.of(new SchoolImport.Row(2, Map.of("studentEmail", student.getEmail(), "classCode", "10A1", "schoolYear", "2026-2027")))));
        assertTrue(preview.canCommit());
        doAnswer(invocation -> { if (invocation.getArgument(0) == target) target.setIsActive(false); return null; }).when(entityManager).refresh(any(), eq(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        assertThrows(ApiException.class, () -> service.commit(schoolId, reviewed(preview)));
        verifyNoInteractions(admin, classService);
    }
    @Test void refreshedUserMovedToAnotherSchoolCannotBeEnrolled() {
        SchoolClass target = new SchoolClass(); target.setId(UUID.randomUUID()); target.setSchool(school); target.setName("10A1"); target.setSchoolYear("2026-2027"); target.setIsActive(true);
        when(classes.findBySchoolIdAndIsActiveTrue(schoolId)).thenReturn(List.of(target));
        User student = user(8, "STUDENT", "student@example.edu.vn");
        when(users.findFirstByEmailIgnoreCase(student.getEmail())).thenReturn(Optional.of(student));
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.ENROLLMENTS, List.of(new SchoolImport.Row(2, Map.of("studentEmail", student.getEmail(), "classCode", "10A1", "schoolYear", "2026-2027")))));
        School other = new School(); other.setId(UUID.randomUUID());
        doAnswer(invocation -> { if (invocation.getArgument(0) == student) student.setSchool(other); return null; }).when(entityManager).refresh(any(), eq(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        assertThrows(ApiException.class, () -> service.commit(schoolId, reviewed(preview)));
        verifyNoInteractions(admin, classService);
    }
    @Test void schoolBoundaryAndDepartmentHeadCapabilitiesAreEnforced() {
        UUID otherSchool = UUID.randomUUID();
        assertThrows(ApiException.class, () -> service.preview(otherSchool, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "a@example.edu.vn")))));
        actor.getRole().setName("STAFF");
        when(roles.canManageSchool(actor, schoolId)).thenReturn(false);
        when(accountAccess.isDepartmentHead(actor)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "a@example.edu.vn")))));
        assertThrows(ApiException.class, () -> service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.CLASSES, List.of(new SchoolImport.Row(2, Map.of("classCode", "10A1", "gradeLevel", "10", "schoolYear", "2026-2027"))))));
        verifyNoInteractions(admin, classService);
    }
    @Test void enrollmentRejectsAnotherSchoolUserAndOnlyUsesClassInThisSchool() {
        SchoolClass target = new SchoolClass(); target.setId(UUID.randomUUID()); target.setSchool(school); target.setName("10A1"); target.setSchoolYear("2026-2027");
        when(classes.findBySchoolIdAndIsActiveTrue(schoolId)).thenReturn(List.of(target));
        User outsider = user(8, "STUDENT", "outsider@example.edu.vn"); School other = new School(); other.setId(UUID.randomUUID()); outsider.setSchool(other);
        when(users.findFirstByEmailIgnoreCase(outsider.getEmail())).thenReturn(Optional.of(outsider));
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.ENROLLMENTS, List.of(new SchoolImport.Row(2, Map.of("studentEmail", outsider.getEmail(), "classCode", "10A1", "schoolYear", "2026-2027")))));
        assertFalse(preview.canCommit()); assertTrue(preview.rows().getFirst().errors().contains("Học sinh không hợp lệ trong trường này."));
        verifyNoInteractions(admin, classService);
    }
    @Test void rejectsOversizedJsonAndUnknownSchoolReferencesWithoutWriting() {
        assertThrows(ApiException.class, () -> service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, java.util.stream.IntStream.range(1, 202).mapToObj(i -> row(i, i + "@example.edu.vn")).toList())));
        assertThrows(ApiException.class, () -> service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(new SchoolImport.Row(2, Map.of("schoolId", UUID.randomUUID().toString()))))));
        verifyNoInteractions(admin, classService);
    }
    @Test void transactionAdviceRollsBackEarlierRowsWhenLaterCreationFails() {
        var preview = service.preview(schoolId, new SchoolImport.Request(SchoolImport.Kind.USERS, List.of(row(2, "a@example.edu.vn"), row(3, "b@example.edu.vn"))));
        List<String> persisted = new ArrayList<>();
        when(admin.createUser(any())).thenAnswer(invocation -> {
            CreateManagedUserRequest request = invocation.getArgument(0);
            if (request.email().startsWith("b@")) throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "concurrent duplicate");
            persisted.add(request.email()); return created(3);
        });
        class BatchTransaction extends AbstractPlatformTransactionManager {
            boolean rolledBack;
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; persisted.clear(); }
        }
        BatchTransaction tx = new BatchTransaction();
        ProxyFactory factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        SchoolImportService transactional = (SchoolImportService) factory.getProxy();
        assertThrows(ApiException.class, () -> transactional.commit(schoolId, reviewed(preview)));
        assertTrue(tx.rolledBack); assertTrue(persisted.isEmpty()); verify(admin, times(2)).createUser(any());
    }
}
