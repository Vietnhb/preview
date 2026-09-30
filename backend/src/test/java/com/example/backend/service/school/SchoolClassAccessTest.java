package com.example.backend.service.school;

import com.example.backend.dto.school.SchoolClassRequest;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.school.ClassEnrollment;
import com.example.backend.entity.school.School;
import com.example.backend.entity.school.SchoolClass;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.ClassEnrollmentRepository;
import com.example.backend.repository.school.ClassTeacherAssignmentRepository;
import com.example.backend.repository.school.SchoolClassRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.RoleValidationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchoolClassAccessTest {
    private final SchoolClassRepository classes = mock(SchoolClassRepository.class);
    private final ClassEnrollmentRepository enrollments = mock(ClassEnrollmentRepository.class);
    private final ClassTeacherAssignmentRepository teachers = mock(ClassTeacherAssignmentRepository.class);
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LicenseCheckService license = mock(LicenseCheckService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final SchoolClassService service = new SchoolClassService(classes, enrollments, teachers, schools, users, current,
        new RoleValidationService(users), license, new AccountAccessService(), entityManager);
    private final School school = school();
    private School school() { School school = new School(); school.setId(UUID.randomUUID()); school.setName("School"); return school; }
    private User user(String role, School school) {
        User user = new User(); user.setId(1); user.setSchool(school); Role accountRole = new Role(); accountRole.setName(role); user.setRole(accountRole); return user;
    }
    private SchoolClass schoolClass(School school) {
        SchoolClass schoolClass = new SchoolClass(); schoolClass.setId(UUID.randomUUID()); schoolClass.setSchool(school);
        schoolClass.setName("10A1"); schoolClass.setSchoolYear("2026-2027"); schoolClass.setGradeLevel(10); schoolClass.setIsActive(true); return schoolClass;
    }
    private SchoolClassRequest request() { return new SchoolClassRequest(" 10A1 ", 10, "2026-2027", "Vật lý"); }

    @ParameterizedTest @ValueSource(strings = {"MANAGER", "ADMIN", "REVIEWER", "STUDENT", "STAFF"})
    void classCreationIsReservedForSchoolEvenWhenStaffIsDepartmentHead(String role) {
        User actor = user(role, school); actor.setStaffType("DEPARTMENT_HEAD"); when(current.requireCurrentUser()).thenReturn(actor);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.create(school.getId(), request())).getStatus());
        verifyNoInteractions(classes, schools, entityManager);
    }

    @Test void schoolCreatesClassUnderSchoolLockBeforeCheckingUniqueness() {
        User actor = user("SCHOOL", school); when(current.requireCurrentUser()).thenReturn(actor);
        when(schools.findByIdForUpdate(school.getId())).thenReturn(Optional.of(school));
        UUID classId = UUID.randomUUID();
        when(classes.save(any())).thenAnswer(call -> {
            SchoolClass added = call.getArgument(0); added.setId(classId); when(classes.findById(classId)).thenReturn(Optional.of(added)); return added;
        });
        var created = service.create(school.getId(), request()); assertEquals("10A1", created.name());
        assertEquals("2026-2027", created.schoolYear()); verify(license).requireWriteAccess(actor);
        var order = inOrder(schools, entityManager, classes);
        order.verify(schools).findByIdForUpdate(school.getId()); order.verify(entityManager).refresh(school, LockModeType.PESSIMISTIC_WRITE);
        order.verify(classes).existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(school.getId(), "10A1", "2026-2027");
        order.verify(classes).save(any());
    }

    @Test void schoolCannotCreateInAnotherSchool() {
        when(current.requireCurrentUser()).thenReturn(user("SCHOOL", school));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.create(UUID.randomUUID(), request())).getStatus());
        verifyNoInteractions(schools, classes, entityManager);
    }

    @Test void duplicateClassIsRejectedAfterAcquiringSchoolLock() {
        when(current.requireCurrentUser()).thenReturn(user("SCHOOL", school));
        when(schools.findByIdForUpdate(school.getId())).thenReturn(Optional.of(school));
        when(classes.existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(school.getId(), "10A1", "2026-2027")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.create(school.getId(), request())).getStatus());
        verify(entityManager).refresh(school, LockModeType.PESSIMISTIC_WRITE); verify(classes, never()).save(any());
    }

    @Test void departmentHeadCanAssignOwnTeachersButCannotReachAnotherSchoolOrClass() {
        User head = user("STAFF", school); head.setStaffType("DEPARTMENT_HEAD"); when(current.requireCurrentUser()).thenReturn(head);
        SchoolClass target = schoolClass(school); when(classes.findById(target.getId())).thenReturn(Optional.of(target));
        User teacher = user("STAFF", school); teacher.setId(2); when(users.findById(2)).thenReturn(Optional.of(teacher));
        when(teachers.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(2, service.assignTeacher(school.getId(), target.getId(), 2).teacherId());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.assignTeacher(UUID.randomUUID(), target.getId(), 2)).getStatus());
        target.setSchool(school());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.assignTeacher(school.getId(), target.getId(), 2)).getStatus());
        verify(teachers, times(1)).save(any());
    }

    @Test void teacherMustBeActiveStaffFromTheSameSchool() {
        User head = user("STAFF", school); head.setStaffType("DEPARTMENT_HEAD"); when(current.requireCurrentUser()).thenReturn(head);
        SchoolClass target = schoolClass(school); when(classes.findById(target.getId())).thenReturn(Optional.of(target));
        User teacher = user("STAFF", school()); when(users.findById(2)).thenReturn(Optional.of(teacher));
        assertThrows(ApiException.class, () -> service.assignTeacher(school.getId(), target.getId(), 2));
        teacher.setSchool(school); teacher.setActive(false);
        assertThrows(ApiException.class, () -> service.assignTeacher(school.getId(), target.getId(), 2));
        teacher.setActive(true); teacher.getRole().setName("STUDENT");
        assertThrows(ApiException.class, () -> service.assignTeacher(school.getId(), target.getId(), 2));
        verify(teachers, never()).save(any());
    }

    @Test void ordinaryTeacherHasNoSchoolManagementAccess() {
        when(current.requireCurrentUser()).thenReturn(user("STAFF", school));
        assertThrows(ApiException.class, () -> service.list(school.getId())); verifyNoInteractions(classes, teachers, enrollments);
    }

    @Test void studentClassesExcludePreviousSchoolsAndArchivedClasses() {
        User student = user("STUDENT", school); when(current.requireCurrentUser()).thenReturn(student);
        SchoolClass own = schoolClass(school), foreign = schoolClass(school()), archived = schoolClass(school); archived.setIsActive(false);
        ClassEnrollment ownEnrollment = new ClassEnrollment(); ownEnrollment.setSchoolClass(own);
        ClassEnrollment foreignEnrollment = new ClassEnrollment(); foreignEnrollment.setSchoolClass(foreign);
        ClassEnrollment archivedEnrollment = new ClassEnrollment(); archivedEnrollment.setSchoolClass(archived);
        when(enrollments.findActiveEnrollmentsByStudentId(student.getId())).thenReturn(List.of(ownEnrollment, foreignEnrollment, archivedEnrollment));
        var visible = service.mineForStudent(); assertEquals(List.of(own.getId()), visible.stream().map(SchoolClassService.StudentClassSummary::id).toList());
        verify(teachers, never()).findByClassIdAndIsActiveTrue(foreign.getId());
        verify(teachers, never()).findByClassIdAndIsActiveTrue(archived.getId());
    }
}
