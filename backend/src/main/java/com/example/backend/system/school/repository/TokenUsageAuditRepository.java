package com.example.backend.system.school.repository;

import com.example.backend.system.school.model.entity.TokenUsageAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenUsageAuditRepository extends JpaRepository<TokenUsageAudit, UUID> {
    List<TokenUsageAudit> findTop200BySchoolIdOrderByRecordedAtDesc(UUID schoolId);
}
