package com.example.backend.repository;

import com.example.backend.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByOwnerIdOrderByCreatedAtDesc(Integer ownerId);
    Optional<Simulation> findByIdAndOwnerId(UUID id, Integer ownerId);
}
