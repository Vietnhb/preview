package com.example.backend.repository.library;

import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.entity.enums.LibraryModerationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    @Query("select i from LibraryItem i where i.id = :id")
    Optional<LibraryItem> findByIdForUpdate(@Param("id") UUID id);
    Optional<LibraryItem> findBySimulationIdAndOwnerId(UUID simulationId, Integer ownerId);

    Optional<LibraryItem> findByIdAndOwnerIdAndActiveTrue(UUID id, Integer ownerId);

    @Query("""
            select i from LibraryItem i
            where i.simulation.id = :simulationId
              and i.active = true
              and i.visibility in :visibilities
              and i.moderationStatus in :moderationStatuses
              and (i.visibility = :publicVisibility
                   or i.sharedInstitutionId is null or i.sharedInstitutionId = ''
                   or i.sharedInstitutionId = :institutionId)
            """)
    Optional<LibraryItem> findVisiblePublishedSimulation(@Param("simulationId") UUID simulationId,
                                                          @Param("visibilities") java.util.Set<Visibility> visibilities,
                                                          @Param("publicVisibility") Visibility publicVisibility,
                                                          @Param("moderationStatuses") java.util.Set<LibraryModerationStatus> moderationStatuses,
                                                          @Param("institutionId") String institutionId);

    List<LibraryItem> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(Integer ownerId);

    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    @Query("""
            select i from LibraryItem i
            where i.active = true
              and (:topic is null or lower(i.specification.topic) = lower(:topic))
              and (
                  i.owner.id = :ownerId
                  or (
                      i.visibility <> :personalVisibility
                      and i.moderationStatus in :publishedStatuses
                      and (
                          i.visibility = :publicVisibility
                          or i.sharedInstitutionId is null
                          or trim(i.sharedInstitutionId) = ''
                          or i.sharedInstitutionId = :institutionId
                      )
                  )
              )
            """)
    List<LibraryItem> findSearchVisibleItems(@Param("ownerId") Integer ownerId,
                                              @Param("institutionId") String institutionId,
                                              @Param("personalVisibility") Visibility personalVisibility,
                                              @Param("publicVisibility") Visibility publicVisibility,
                                              @Param("publishedStatuses") Set<LibraryModerationStatus> publishedStatuses,
                                              @Param("topic") String topic);

    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    @Query("""
            select i from LibraryItem i
            where i.active = true
              and i.visibility = :publicVisibility
              and i.moderationStatus in :publishedStatuses
              and (:topic is null or lower(i.specification.topic) = lower(:topic))
            """)
    List<LibraryItem> findCommunityItems(@Param("publicVisibility") Visibility publicVisibility,
                                         @Param("publishedStatuses") Set<LibraryModerationStatus> publishedStatuses,
                                         @Param("topic") String topic);

    boolean existsByFolderIdAndActiveTrue(UUID folderId);

    long countByFolderIdAndActiveTrue(UUID folderId);

    List<LibraryItem> findByVisibilityAndModerationStatusOrderByCreatedAtAsc(Visibility visibility, LibraryModerationStatus status);
    List<LibraryItem> findByVisibilityInAndModerationStatusOrderByCreatedAtAsc(java.util.Set<Visibility> visibilities, LibraryModerationStatus status);
    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    Page<LibraryItem> findByVisibilityInAndModerationStatusOrderByCreatedAtAsc(java.util.Set<Visibility> visibilities,
                                                                                 LibraryModerationStatus status,
                                                                                 Pageable pageable);
}
