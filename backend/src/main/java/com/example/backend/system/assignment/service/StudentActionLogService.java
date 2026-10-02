package com.example.backend.system.assignment.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.assignment.dto.AssignmentContracts.StudentActionLogRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.StudentActionLogResponse;
import com.example.backend.system.assignment.dto.AssignmentContracts;
import com.example.backend.system.assignment.model.entity.Assignment;
import com.example.backend.system.assignment.model.entity.StudentActionLog;
import com.example.backend.system.assignment.repository.AssignmentRepository;
import com.example.backend.system.assignment.repository.StudentActionLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentActionLogService {
    private final StudentActionLogRepository logRepository;
    private final AssignmentRepository assignmentRepository;
    private final CurrentUserService currentUser;

    @Transactional
    public StudentActionLogResponse create(StudentActionLogRequest request) {
        Integer studentId = currentUser.requireCurrentUser().getId();
        boolean assigned = assignmentRepository.findById(request.assignmentId())
                .map(item -> item.getAssignedStudentIds().contains(studentId))
                .orElse(false);
        if (!assigned) {
            throw ApiException.forbidden("Assignment is not assigned to this student");
        }
        StudentActionLog log = new StudentActionLog();
        log.setStudentId(studentId);
        log.setAssignmentId(request.assignmentId());
        log.setAction(request.action().trim());
        log.setPayload(request.payload());
        return StudentActionLogResponse.from(logRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<StudentActionLogResponse> mine() {
        return logRepository.findTop200ByStudentIdOrderByOccurredAtDesc(currentUser.requireCurrentUser().getId())
                .stream().map(StudentActionLogResponse::from).toList();
    }
}
