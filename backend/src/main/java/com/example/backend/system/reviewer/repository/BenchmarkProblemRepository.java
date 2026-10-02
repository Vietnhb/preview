package com.example.backend.system.reviewer.repository;

import com.example.backend.system.reviewer.model.entity.Adjudication;
import com.example.backend.system.reviewer.model.entity.BenchmarkProblem;
import com.example.backend.system.reviewer.model.entity.GoldAnnotation;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenchmarkProblemRepository extends JpaRepository<BenchmarkProblem, UUID> {
    @Query("""
            select benchmark
            from BenchmarkProblem benchmark
            where (:status is null or benchmark.status = :status)
              and (:topic is null or lower(benchmark.topic) = lower(:topic))
              and (:gradeScope is null or benchmark.gradeScope = :gradeScope)
              and (:sourceCategory is null or lower(benchmark.sourceCategory) like lower(concat('%', :sourceCategory, '%')))
            """)
    Page<BenchmarkProblem> search(@Param("status") String status, @Param("topic") String topic,
                                  @Param("gradeScope") String gradeScope,
                                  @Param("sourceCategory") String sourceCategory, Pageable pageable);

    @Query("""
            select distinct benchmark
            from BenchmarkProblem benchmark
            where benchmark.active = true
              and benchmark.status = 'GOLD_READY'
              and (
                exists (
                    select adjudication.id
                    from Adjudication adjudication
                    where adjudication.benchmarkProblem = benchmark
                )
                or (
                    (select count(annotation.id)
                     from GoldAnnotation annotation
                     where annotation.benchmarkProblem = benchmark) = 2
                    and exists (
                        select firstAnnotation.id
                        from GoldAnnotation firstAnnotation, GoldAnnotation secondAnnotation
                        where firstAnnotation.benchmarkProblem = benchmark
                          and secondAnnotation.benchmarkProblem = benchmark
                          and firstAnnotation.id <> secondAnnotation.id
                          and firstAnnotation.annotatorReference <> secondAnnotation.annotatorReference
                          and firstAnnotation.goldSpecification = secondAnnotation.goldSpecification
                    )
                )
              )
            """)
    java.util.List<BenchmarkProblem> findFinalizedActive();
}
