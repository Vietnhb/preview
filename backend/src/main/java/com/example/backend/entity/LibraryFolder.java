package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "library_folders", uniqueConstraints =
        @UniqueConstraint(name = "uk_library_folder_owner_name", columnNames = {"owner_id", "name_key"}))
@Getter
@Setter
public class LibraryFolder extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "name_key", nullable = false, length = 120)
    private String nameKey;

    @Column(nullable = false)
    private boolean active = true;
}
