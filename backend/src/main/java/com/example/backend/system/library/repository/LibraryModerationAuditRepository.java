package com.example.backend.system.library.repository;

import com.example.backend.system.library.model.entity.LibraryModerationAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryModerationAuditRepository extends JpaRepository<LibraryModerationAudit, UUID> {
    List<LibraryModerationAudit> findTop100ByLibraryItemIdOrderByDecidedAtDesc(UUID libraryItemId);
}
