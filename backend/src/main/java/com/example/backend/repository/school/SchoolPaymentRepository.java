package com.example.backend.repository.school;

import com.example.backend.entity.school.SchoolPayment;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SchoolPaymentRepository extends JpaRepository<SchoolPayment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SchoolPayment p where p.id = :id")
    Optional<SchoolPayment> findLockedById(@Param("id") UUID id);
    List<SchoolPayment> findTop20ByStatusInOrderByPaidAtDesc(List<String> statuses);
    Optional<SchoolPayment> findFirstByManagerIdOrderByCreatedAtDesc(Integer managerId);
    List<SchoolPayment> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
    List<SchoolPayment> findByStatusAndCreatedAtBefore(String status, java.time.Instant cutoff);
}
