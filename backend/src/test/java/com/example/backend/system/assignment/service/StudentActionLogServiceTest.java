package com.example.backend.system.assignment.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.assignment.dto.AssignmentContracts.StudentActionLogRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts;
import com.example.backend.system.assignment.model.entity.Assignment;
import com.example.backend.system.assignment.model.entity.StudentActionLog;
import com.example.backend.system.assignment.repository.AssignmentRepository;
import com.example.backend.system.assignment.repository.StudentActionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentActionLogServiceTest {
    private final StudentActionLogRepository logs = mock(StudentActionLogRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final StudentActionLogService service = new StudentActionLogService(logs, assignments, currentUser);

    @Test
    void unassignedStudentCannotWriteActionLog() {
        User student = new User(); student.setId(4); when(currentUser.requireCurrentUser()).thenReturn(student);
        UUID id = UUID.randomUUID(); Assignment assignment = new Assignment(); assignment.setAssignedStudentIds(Set.of(5));
        when(assignments.findById(id)).thenReturn(Optional.of(assignment));
        assertThrows(ApiException.class, () -> service.create(new StudentActionLogRequest(id, "START", null)));
        verifyNoInteractions(logs);
    }

    @Test
    void ownLogResponseRetainsExistingJsonFieldsWithoutReturningJpaEntity() {
        User student = new User(); student.setId(4); when(currentUser.requireCurrentUser()).thenReturn(student);
        StudentActionLog log = new StudentActionLog(); log.setId(UUID.randomUUID()); log.setStudentId(4);
        log.setAssignmentId(UUID.randomUUID()); log.setAction("START");
        Instant time = Instant.parse("2026-09-30T01:00:00Z"); log.setCreatedAt(time); log.setUpdatedAt(time); log.setOccurredAt(time);
        log.setPayload(new ObjectMapper().createObjectNode().put("value", 1));
        when(logs.findTop200ByStudentIdOrderByOccurredAtDesc(4)).thenReturn(List.of(log));
        var response = service.mine().getFirst();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
        assertEquals(Set.of("id", "createdAt", "updatedAt", "studentId", "assignmentId", "action", "payload", "occurredAt"),
                fields(json));
        assertEquals(4, json.path("studentId").asInt()); assertEquals(1, json.path("payload").path("value").asInt());
        assertEquals(log.getId(), response.id()); assertEquals(time, response.createdAt());
        verify(logs).findTop200ByStudentIdOrderByOccurredAtDesc(4);
    }

    private static Set<String> fields(com.fasterxml.jackson.databind.JsonNode value) {
        Set<String> fields = new java.util.HashSet<>(); value.fieldNames().forEachRemaining(fields::add); return fields;
    }
}
