package com.example.backend.entity.audit;

import com.example.backend.entity.account.User;
import com.example.backend.entity.common.AuditedEntity;
import com.example.backend.entity.school.School;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

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
