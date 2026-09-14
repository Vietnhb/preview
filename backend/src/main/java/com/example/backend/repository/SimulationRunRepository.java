package com.example.backend.repository;

import com.example.backend.entity.SimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {
    List<SimulationRun> findBySimulationIdOrderByCreatedAtDesc(UUID simulationId);
}
