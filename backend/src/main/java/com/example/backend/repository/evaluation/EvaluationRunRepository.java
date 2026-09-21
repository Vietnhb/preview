package com.example.backend.repository.evaluation;

import com.example.backend.entity.evaluation.EvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {
    Page<EvaluationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select run from EvaluationRun run
            where (:status is null or run.status = :status)
            order by run.createdAt desc
            """)
    Page<EvaluationRun> search(@Param("status") String status, Pageable pageable);
}
