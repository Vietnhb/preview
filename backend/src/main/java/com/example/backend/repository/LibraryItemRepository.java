package com.example.backend.repository;

import com.example.backend.entity.LibraryItem;
import com.example.backend.enums.Visibility;
import com.example.backend.enums.LibraryModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            select i from LibraryItem i
            where i.simulation.id = :simulationId
              and i.active = true
              and i.visibility = :visibility
              and (i.sharedInstitutionId is null or i.sharedInstitutionId = ''
                   or i.sharedInstitutionId = :institutionId)
            """)
    Optional<LibraryItem> findVisibleSharedSimulation(@Param("simulationId") UUID simulationId,
                                                       @Param("visibility") Visibility visibility,
                                                       @Param("institutionId") String institutionId);

    List<LibraryItem> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(Integer ownerId);

    boolean existsByFolderIdAndActiveTrue(UUID folderId);

    long countByFolderIdAndActiveTrue(UUID folderId);

    List<LibraryItem> findByVisibilityAndModerationStatusOrderByCreatedAtAsc(Visibility visibility, LibraryModerationStatus status);
}
