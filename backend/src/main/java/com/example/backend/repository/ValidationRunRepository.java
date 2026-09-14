package com.example.backend.repository;

import com.example.backend.entity.ValidationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValidationRunRepository extends JpaRepository<ValidationRun, UUID> {
    List<ValidationRun> findBySimulationIdOrderByCreatedAtDesc(UUID simulationId);
    long countByPassedFalse();
}
