package com.example.backend.entity.audit;

import com.example.backend.entity.common.AuditedEntity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_action_logs")
@Getter @Setter
public class StudentActionLog extends AuditedEntity {
    @Column(name = "student_id", nullable = false) private Integer studentId;
    @Column(name = "assignment_id", nullable = false) private UUID assignmentId;
    @Column(nullable = false, length = 64) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private JsonNode payload;
    @Column(nullable = false) private Instant occurredAt = Instant.now();
}
