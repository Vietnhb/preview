package com.example.backend.repository.audit;

import com.example.backend.entity.audit.TokenUsageAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TokenUsageAuditRepository extends JpaRepository<TokenUsageAudit, UUID> {
    List<TokenUsageAudit> findTop200BySchoolIdOrderByRecordedAtDesc(UUID schoolId);
}
