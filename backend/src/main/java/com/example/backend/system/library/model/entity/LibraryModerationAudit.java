package com.example.backend.system.library.model.entity;

import com.example.backend.base.crud.model.entity.AuditedEntity;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

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
