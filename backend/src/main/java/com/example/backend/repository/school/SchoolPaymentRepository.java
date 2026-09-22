package com.example.backend.repository.school;

import com.example.backend.entity.school.SchoolPayment;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SchoolPaymentRepository extends JpaRepository<SchoolPayment, UUID> {
    interface RevenueTotals {
        Long getPaidTransactions();
        Long getPendingTransactions();
        Long getReviewTransactions();
        Long getGrossPaidVnd();
    }

    @Query("""
            select
                coalesce(sum(case when p.status = 'PAID' then 1L else 0L end), 0L) as paidTransactions,
                coalesce(sum(case when p.status = 'PENDING' then 1L else 0L end), 0L) as pendingTransactions,
                coalesce(sum(case when p.status = 'REQUIRES_REVIEW' then 1L else 0L end), 0L) as reviewTransactions,
                coalesce(sum(case when p.status = 'PAID' then p.amountVnd else 0L end), 0L) as grossPaidVnd
            from SchoolPayment p
            """)
    RevenueTotals summarizeRevenue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SchoolPayment p where p.id = :id")
    Optional<SchoolPayment> findLockedById(@Param("id") UUID id);
    List<SchoolPayment> findTop20ByStatusInOrderByPaidAtDesc(List<String> statuses);
    Optional<SchoolPayment> findFirstByManagerIdOrderByCreatedAtDesc(Integer managerId);
    Optional<SchoolPayment> findFirstByRegistrationEmailIgnoreCaseOrderByCreatedAtDesc(String registrationEmail);
    @Query("select count(p) > 0 from SchoolPayment p where p.purpose = 'REGISTRATION' and p.status = 'PENDING' and (lower(p.registrationEmail) = lower(:email) or upper(p.registrationSchoolCode) = upper(:code) or lower(p.registrationSchoolName) = lower(:name))")
    boolean hasPendingRegistrationConflict(@Param("email") String email, @Param("code") String code, @Param("name") String name);
    List<SchoolPayment> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
    List<SchoolPayment> findByStatusAndCreatedAtBefore(String status, java.time.Instant cutoff);
}
