package com.example.backend.entity.problem;

import com.example.backend.entity.common.AuditedEntity;
import com.example.backend.entity.enums.LifecycleStatus;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schema_versions", uniqueConstraints =
        @UniqueConstraint(name = "uk_schema_versions_identity", columnNames = {"schema_id", "version"}))
@Getter
@Setter
public class SchemaVersion extends AuditedEntity {
    @Column(name = "schema_id", nullable = false, length = 80)
    private String schemaId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(nullable = false, length = 24)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode definition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.DRAFT;
}
