package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "topic_module_releases")
@Getter
@Setter
public class TopicModuleRelease extends AuditedEntity {
    @Column(nullable = false, length = 80)
    private String topic;

    @Column(nullable = false, length = 120)
    private String moduleName;

    @Column(nullable = false, length = 80)
    private String schemaId;

    @Column(nullable = false, length = 24)
    private String schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.DRAFT;
}
