package com.example.backend.repository;

import com.example.backend.entity.EvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {
}
