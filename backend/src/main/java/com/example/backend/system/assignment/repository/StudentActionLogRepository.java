package com.example.backend.system.assignment.repository;

import com.example.backend.system.assignment.model.entity.StudentActionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentActionLogRepository extends JpaRepository<StudentActionLog, UUID> {
    List<StudentActionLog> findTop200ByStudentIdOrderByOccurredAtDesc(Integer studentId);
}
