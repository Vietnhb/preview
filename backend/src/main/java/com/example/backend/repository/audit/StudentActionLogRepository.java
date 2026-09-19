package com.example.backend.repository.audit;

import com.example.backend.entity.audit.StudentActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface StudentActionLogRepository extends JpaRepository<StudentActionLog, UUID> {
    List<StudentActionLog> findTop200ByStudentIdOrderByOccurredAtDesc(Integer studentId);
}
