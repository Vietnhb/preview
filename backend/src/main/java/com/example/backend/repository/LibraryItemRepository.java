package com.example.backend.repository;

import com.example.backend.entity.LibraryItem;
import com.example.backend.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, UUID> {
    Page<LibraryItem> findByActiveTrueAndOwnerIdOrActiveTrueAndVisibility(
            Integer ownerId, Visibility visibility, Pageable pageable);

    Optional<LibraryItem> findBySimulationIdAndOwnerId(UUID simulationId, Integer ownerId);

    Optional<LibraryItem> findByIdAndOwnerIdAndActiveTrue(UUID id, Integer ownerId);

    Optional<LibraryItem> findBySimulationIdAndActiveTrueAndVisibility(UUID simulationId, Visibility visibility);

    List<LibraryItem> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(Integer ownerId);

    boolean existsByFolderIdAndActiveTrue(UUID folderId);

    long countByFolderIdAndActiveTrue(UUID folderId);
}
