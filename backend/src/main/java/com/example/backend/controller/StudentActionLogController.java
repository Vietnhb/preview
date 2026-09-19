package com.example.backend.controller;

import com.example.backend.entity.StudentActionLog;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.StudentActionLogRepository;
import com.example.backend.repository.AssignmentRepository;
import com.example.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student/action-logs")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentActionLogController {
    private final StudentActionLogRepository logs;
    private final AssignmentRepository assignments;
    private final CurrentUserService currentUser;
    public record ActionRequest(@NotNull UUID assignmentId, @NotBlank @Size(max = 64) String action, com.fasterxml.jackson.databind.JsonNode payload) { }
    @PostMapping public StudentActionLog create(@Valid @RequestBody ActionRequest request) {
        Integer studentId = currentUser.requireCurrentUser().getId();
        if (!assignments.findById(request.assignmentId()).filter(item -> item.getAssignedStudentIds().contains(studentId)).isPresent())
            throw new ApiException(HttpStatus.FORBIDDEN, "Assignment is not assigned to this student");
        StudentActionLog log = new StudentActionLog(); log.setStudentId(studentId); log.setAssignmentId(request.assignmentId()); log.setAction(request.action().trim()); log.setPayload(request.payload()); return logs.save(log);
    }
    @GetMapping public List<StudentActionLog> mine() { return logs.findTop200ByStudentIdOrderByOccurredAtDesc(currentUser.requireCurrentUser().getId()); }
}
