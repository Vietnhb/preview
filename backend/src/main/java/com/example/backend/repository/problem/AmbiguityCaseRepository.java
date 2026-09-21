package com.example.backend.repository.problem;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.problem.Specification;

public interface AmbiguityCaseRepository extends JpaRepository<AmbiguityCase, UUID> {
    List<AmbiguityCase> findByStatus(AmbiguityStatus status);
    List<AmbiguityCase> findByStatusOrderByCreatedAtAsc(AmbiguityStatus status);

    @Query("""
            select item from AmbiguityCase item
            where item.status = :status
              and (item.claimedBy is null or item.claimExpiresAt < :now or item.claimedBy = :actorId)
            order by item.createdAt asc
            """)
    List<AmbiguityCase> findQueue(@Param("status") AmbiguityStatus status,
                                  @Param("now") java.time.Instant now,
                                  @Param("actorId") Integer actorId);

    @Query("""
            select item from AmbiguityCase item
            where item.status = :status
              and (item.claimedBy is null or item.claimExpiresAt < :now or item.claimedBy = :actorId)
              and (:topic is null or lower(item.specification.topic) = lower(:topic))
            order by item.createdAt asc
            """)
    Page<AmbiguityCase> findQueuePage(@Param("status") AmbiguityStatus status,
                                      @Param("now") java.time.Instant now,
                                      @Param("actorId") Integer actorId,
                                      @Param("topic") String topic, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from AmbiguityCase item where item.id = :id")
    Optional<AmbiguityCase> findByIdForUpdate(@Param("id") UUID id);

    Optional<AmbiguityCase> findByIdAndSpecification(UUID id, Specification specification);
}
