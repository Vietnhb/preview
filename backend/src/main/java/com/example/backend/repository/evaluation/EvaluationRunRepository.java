package com.example.backend.repository.evaluation;

import com.example.backend.entity.evaluation.EvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {
}
