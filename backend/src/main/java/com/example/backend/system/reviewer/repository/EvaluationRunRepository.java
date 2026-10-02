package com.example.backend.system.reviewer.repository;

import com.example.backend.system.reviewer.model.entity.EvaluationRun;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {
    Page<EvaluationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select run from EvaluationRun run
            where (:status is null or run.status = :status)
            order by run.createdAt desc
            """)
    Page<EvaluationRun> search(@Param("status") String status, Pageable pageable);
}
