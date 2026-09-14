package com.example.backend.repository;

import com.example.backend.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByTeacherIdOrderByCreatedAtDesc(Integer teacherId);
    List<Assignment> findByAssignedStudentIdsContaining(Integer studentId);
}
