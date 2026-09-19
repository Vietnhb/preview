package com.example.backend.repository;

import com.example.backend.entity.StudentActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StudentActionLogRepository extends JpaRepository<StudentActionLog, UUID> {
    List<StudentActionLog> findTop200ByStudentIdOrderByOccurredAtDesc(Integer studentId);
}
