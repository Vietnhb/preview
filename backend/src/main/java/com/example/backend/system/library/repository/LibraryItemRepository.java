package com.example.backend.system.library.repository;

import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.model.enums.Visibility;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, UUID> {
    @Query(value = "select count(*) from library_likes where item_id = :itemId", nativeQuery = true)
    long countLikes(@Param("itemId") UUID itemId);

    @Query(value = "select exists(select 1 from library_likes where item_id = :itemId and user_id = :userId)", nativeQuery = true)
    boolean hasLiked(@Param("itemId") UUID itemId, @Param("userId") Integer userId);

    @Modifying
    @Query(value = "insert into library_likes (item_id, user_id) values (:itemId, :userId) on conflict (item_id, user_id) do nothing", nativeQuery = true)
    int addLike(@Param("itemId") UUID itemId, @Param("userId") Integer userId);

    @Modifying
    @Query(value = "delete from library_likes where item_id = :itemId and user_id = :userId", nativeQuery = true)
    int removeLike(@Param("itemId") UUID itemId, @Param("userId") Integer userId);

    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    @Query("select i from LibraryItem i where i.visibility = com.example.backend.system.library.model.enums.Visibility.SHARED and i.moderationStatus = :status and i.owner.school.id = :schoolId and (i.active = true or i.moderationStatus = com.example.backend.system.library.model.enums.LibraryModerationStatus.REMOVED) order by i.createdAt asc")
    List<LibraryItem> findSchoolModerationItems(@Param("schoolId") UUID schoolId, @Param("status") LibraryModerationStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    @Query("select i from LibraryItem i where i.id = :id")
    Optional<LibraryItem> findByIdForUpdate(@Param("id") UUID id);
    Optional<LibraryItem> findBySimulationIdAndOwnerId(UUID simulationId, Integer ownerId);
    @EntityGraph(attributePaths = {"simulation", "simulation.specification", "owner", "owner.school"})
    Optional<LibraryItem> findFirstBySimulationId(UUID simulationId);

    @EntityGraph(attributePaths = {"simulation", "simulation.specification", "owner", "owner.school"})
    List<LibraryItem> findBySimulationIdAndVisibilityOrderByCreatedAtDesc(UUID simulationId, Visibility visibility);

    Optional<LibraryItem> findByIdAndOwnerIdAndActiveTrue(UUID id, Integer ownerId);

    @Query("""
            select i from LibraryItem i left join i.owner.school ownerSchool
            where i.simulation.id = :simulationId
              and i.active = true
              and i.visibility in :visibilities
              and i.moderationStatus in :moderationStatuses
              and (i.visibility = :publicVisibility
                   or (i.sharedInstitutionId = :institutionId and :institutionId is not null)
                   or ((i.sharedInstitutionId is null or trim(i.sharedInstitutionId) = '')
                       and cast(ownerSchool.id as string) = :institutionId and :institutionId is not null))
            """)
    Optional<LibraryItem> findVisiblePublishedSimulation(@Param("simulationId") UUID simulationId,
                                                          @Param("visibilities") java.util.Set<Visibility> visibilities,
                                                          @Param("publicVisibility") Visibility publicVisibility,
                                                          @Param("moderationStatuses") java.util.Set<LibraryModerationStatus> moderationStatuses,
                                                          @Param("institutionId") String institutionId);

    List<LibraryItem> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(Integer ownerId);

    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    @Query("""
            select i from LibraryItem i left join i.owner.school ownerSchool
            where i.active = true
              and (coalesce(:topic, '') = '' or lower(i.specification.topic) = lower(:topic))
              and (
                  i.owner.id = :ownerId
                  or (
                      i.visibility <> :personalVisibility
                      and i.moderationStatus in :publishedStatuses
                      and (
                          i.visibility = :publicVisibility
                          or (i.sharedInstitutionId = :institutionId and :institutionId is not null)
                          or ((i.sharedInstitutionId is null or trim(i.sharedInstitutionId) = '')
                              and cast(ownerSchool.id as string) = :institutionId and :institutionId is not null)
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
              and (coalesce(:topic, '') = '' or lower(i.specification.topic) = lower(:topic))
            """)
    List<LibraryItem> findCommunityItems(@Param("publicVisibility") Visibility publicVisibility,
                                         @Param("publishedStatuses") Set<LibraryModerationStatus> publishedStatuses,
                                         @Param("topic") String topic);

    boolean existsByFolderIdAndActiveTrue(UUID folderId);

    long countByFolderIdAndActiveTrue(UUID folderId);

    List<LibraryItem> findByVisibilityAndModerationStatusOrderByCreatedAtAsc(Visibility visibility, LibraryModerationStatus status);
    @Query("select i from LibraryItem i where i.visibility in :visibilities and i.moderationStatus = :status and (i.active = true or i.moderationStatus = com.example.backend.system.library.model.enums.LibraryModerationStatus.REMOVED) order by i.createdAt asc")
    List<LibraryItem> findByVisibilityInAndModerationStatusOrderByCreatedAtAsc(@Param("visibilities") java.util.Set<Visibility> visibilities, @Param("status") LibraryModerationStatus status);
    @EntityGraph(attributePaths = {"specification", "owner", "owner.school"})
    @Query("select i from LibraryItem i where i.visibility in :visibilities and i.moderationStatus = :status and (i.active = true or i.moderationStatus = com.example.backend.system.library.model.enums.LibraryModerationStatus.REMOVED) order by i.createdAt asc")
    Page<LibraryItem> findByVisibilityInAndModerationStatusOrderByCreatedAtAsc(@Param("visibilities") java.util.Set<Visibility> visibilities,
                                                                                 @Param("status") LibraryModerationStatus status,
                                                                                 Pageable pageable);
}
