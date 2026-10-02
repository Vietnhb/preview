package com.example.backend.system.school.model.entity;

import com.example.backend.base.crud.model.entity.AuditedEntity;
import com.example.backend.system.account.model.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "token_usage_audits")
@Getter
@Setter
public class TokenUsageAudit extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "school_id", nullable = false)
    private School school;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false) private long tokens;
    @Column(nullable = false, length = 64) private String operation;
    @Column(nullable = false) private LocalDate usageMonth;
    @Column(nullable = false) private Instant recordedAt = Instant.now();
}
