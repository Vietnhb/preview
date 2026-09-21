package com.example.backend.service.assignment;

import com.example.backend.dto.assignment.StudentActionLogRequest;
import com.example.backend.entity.audit.StudentActionLog;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.assignment.AssignmentRepository;
import com.example.backend.repository.audit.StudentActionLogRepository;
import com.example.backend.service.account.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentActionLogService {
    private final StudentActionLogRepository logRepository;
    private final AssignmentRepository assignmentRepository;
    private final CurrentUserService currentUser;

    @Transactional
    public StudentActionLog create(StudentActionLogRequest request) {
        Integer studentId = currentUser.requireCurrentUser().getId();
        boolean assigned = assignmentRepository.findById(request.assignmentId())
                .map(item -> item.getAssignedStudentIds().contains(studentId))
                .orElse(false);
        if (!assigned) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assignment is not assigned to this student");
        }
        StudentActionLog log = new StudentActionLog();
        log.setStudentId(studentId);
        log.setAssignmentId(request.assignmentId());
        log.setAction(request.action().trim());
        log.setPayload(request.payload());
        return logRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<StudentActionLog> mine() {
        return logRepository.findTop200ByStudentIdOrderByOccurredAtDesc(currentUser.requireCurrentUser().getId());
    }
}
