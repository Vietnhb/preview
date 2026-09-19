package com.example.backend.repository;

import com.example.backend.entity.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {
    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, Integer studentId);
    List<AssignmentSubmission> findByAssignmentIdOrderBySubmittedAtDesc(UUID assignmentId);
    boolean existsByAssignmentIdAndStudentId(UUID assignmentId, Integer studentId);
}
