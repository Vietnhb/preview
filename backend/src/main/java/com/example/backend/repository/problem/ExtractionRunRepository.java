package com.example.backend.repository.problem;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.problem.ExtractionRun;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, UUID> {
}
