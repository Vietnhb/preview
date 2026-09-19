package com.example.backend.repository.evaluation;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.evaluation.BenchmarkProblem;

public interface BenchmarkProblemRepository extends JpaRepository<BenchmarkProblem, UUID> {
}
