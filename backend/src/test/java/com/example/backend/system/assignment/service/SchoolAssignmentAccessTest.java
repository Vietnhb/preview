package com.example.backend.system.assignment.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.TestPermissions;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.assignment.model.entity.Assignment;
import com.example.backend.system.assignment.model.enums.AssignmentStatus;
import com.example.backend.system.assignment.repository.AssignmentRepository;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.model.entity.SchoolClass;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchoolAssignmentAccessTest {
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final SchoolAssignmentService service = new SchoolAssignmentService(assignments, current, TestPermissions.access());
    private final UUID schoolId = UUID.randomUUID();
    private User user(String role) {
        User user = new User(); user.setId(1); Role accountRole = new Role(); accountRole.setName(role); user.setRole(accountRole);
        School school = new School(); school.setId(schoolId); user.setSchool(school); return user;
    }
    private User head() { User user = user("STAFF"); TestPermissions.set(user, "TEACH", "DEPARTMENT_HEAD_PHYSICS"); return user; }

    @Test void headReceivesOwnSchoolAssignmentsWithPageMetadataAndClasslessRows() {
        when(current.requireCurrentUser()).thenReturn(head());
        User teacher = user("STAFF"); teacher.setId(7); teacher.setFullName("Teacher");
        Assignment withClass = new Assignment(); withClass.setId(UUID.randomUUID()); withClass.setTitle("Class assignment"); withClass.setTeacher(teacher);
        withClass.setAssignedStudentIds(Set.of(21, 22)); withClass.setStatus(AssignmentStatus.ACTIVE);
        withClass.setAssignedAt(Instant.parse("2026-10-01T03:00:00Z")); withClass.setDueAt(Instant.parse("2026-10-03T03:00:00Z"));
        SchoolClass schoolClass = new SchoolClass(); schoolClass.setId(UUID.randomUUID()); schoolClass.setName("10A1"); schoolClass.setSchoolYear("2026-2027"); withClass.setSchoolClass(schoolClass);
        Assignment direct = new Assignment(); direct.setId(UUID.randomUUID()); direct.setTitle("Direct assignment"); direct.setTeacher(teacher); direct.setAssignedStudentIds(Set.of(23));
        var paging = PageRequest.of(1, 2, Sort.by("createdAt").descending());
        when(assignments.findSchoolAssignments(schoolId, paging)).thenReturn(new PageImpl<>(List.of(withClass, direct), paging, 5));
        var result = service.list(schoolId, 1, 2);
        assertEquals(1, result.getNumber()); assertEquals(2, result.getSize()); assertEquals(5, result.getTotalElements()); assertEquals(3, result.getTotalPages());
        var row = result.getContent().getFirst(); assertEquals(schoolClass.getId(), row.classId()); assertEquals("10A1", row.className());
        assertEquals("2026-2027", row.schoolYear()); assertEquals(7, row.teacherId()); assertEquals("Teacher", row.teacherName());
        assertEquals(2, row.studentCount()); assertEquals("ACTIVE", row.status()); assertEquals(withClass.getDueAt(), row.dueAt());
        assertNull(result.getContent().get(1).classId()); assertEquals(1, result.getContent().get(1).studentCount());
        verify(assignments).findSchoolAssignments(schoolId, paging);
    }

    @ParameterizedTest @ValueSource(strings = {"STAFF", "SCHOOL", "MANAGER", "REVIEWER", "STUDENT", "ADMIN"})
    void ordinaryStaffAndOtherRolesCannotUseHeadAssignmentEndpoint(String role) {
        when(current.requireCurrentUser()).thenReturn(user(role));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.list(schoolId, 0, 20)).getStatus());
        verifyNoInteractions(assignments);
    }

    @Test void headCannotInspectAnotherSchoolOrReadAfterDeactivation() {
        User actor = head(); when(current.requireCurrentUser()).thenReturn(actor);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.list(UUID.randomUUID(), 0, 20)).getStatus());
        actor.setActive(false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.list(schoolId, 0, 20)).getStatus());
        verifyNoInteractions(assignments);
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,101"})
    void invalidPagingIsRejectedBeforeQuery(int page, int size) {
        when(current.requireCurrentUser()).thenReturn(head());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.list(schoolId, page, size)).getStatus());
        verifyNoInteractions(assignments);
    }
}
