package com.example.backend.repository;

import com.example.backend.entity.LibraryModerationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LibraryModerationAuditRepository extends JpaRepository<LibraryModerationAudit, UUID> {
    List<LibraryModerationAudit> findTop100ByLibraryItemIdOrderByDecidedAtDesc(UUID libraryItemId);
}
