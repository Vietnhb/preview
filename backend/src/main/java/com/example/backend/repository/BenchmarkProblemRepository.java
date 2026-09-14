package com.example.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.BenchmarkProblem;

public interface BenchmarkProblemRepository extends JpaRepository<BenchmarkProblem, UUID> {
}
