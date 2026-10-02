package com.example.backend.system.physics.repository;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.model.entity.SolverVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolverVersionRepository extends JpaRepository<SolverVersion, UUID> {
    Optional<SolverVersion> findFirstBySchemaIdAndVersion(String schemaId, String version);

    Optional<SolverVersion> findFirstBySchemaIdAndLifecycleStatusOrderByCreatedAtDesc(String schemaId,
            LifecycleStatus lifecycleStatus);
}
