package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "library_moderation_audits")
@Getter
@Setter
public class LibraryModerationAudit extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "library_item_id", nullable = false)
    private LibraryItem libraryItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 16) private LibraryModerationStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 16) private LibraryModerationStatus toStatus;
    @Column(columnDefinition = "text") private String comment;
    @Column(nullable = false) private Instant decidedAt = Instant.now();
}
