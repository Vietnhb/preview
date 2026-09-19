package com.example.backend.repository.simulation;

import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.simulation.SolverVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SolverVersionRepository extends JpaRepository<SolverVersion, UUID> {
    Optional<SolverVersion> findFirstBySchemaIdAndVersion(String schemaId, String version);

    Optional<SolverVersion> findFirstBySchemaIdAndLifecycleStatusOrderByCreatedAtDesc(String schemaId,
            LifecycleStatus lifecycleStatus);
}
