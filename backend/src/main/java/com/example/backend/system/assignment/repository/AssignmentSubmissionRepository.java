package com.example.backend.system.assignment.repository;

import com.example.backend.system.assignment.model.entity.AssignmentSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {
    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, Integer studentId);
    List<AssignmentSubmission> findByAssignmentIdOrderBySubmittedAtDesc(UUID assignmentId);
    boolean existsByAssignmentIdAndStudentId(UUID assignmentId, Integer studentId);
}
