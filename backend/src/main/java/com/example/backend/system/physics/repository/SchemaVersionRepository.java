package com.example.backend.system.physics.repository;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, UUID> {
    List<SchemaVersion> findByLifecycleStatus(LifecycleStatus lifecycleStatus);
    List<SchemaVersion> findAllByLifecycleStatusAndTopicInOrderByTopicAscSchemaIdAscCreatedAtDesc(
            LifecycleStatus lifecycleStatus, List<String> topics);
    List<SchemaVersion> findAllBySchemaIdIgnoreCaseAndLifecycleStatusAndTopicInOrderByCreatedAtDesc(
            String schemaId, LifecycleStatus lifecycleStatus, List<String> topics);
    Optional<SchemaVersion> findFirstBySchemaIdAndVersion(String schemaId, String version);
    Optional<SchemaVersion> findTopBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
            String schemaId, LifecycleStatus lifecycleStatus);
    Optional<SchemaVersion> findTopBySchemaIdIgnoreCaseOrderByCreatedAtDesc(String schemaId);
    List<SchemaVersion> findAllBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
            String schemaId, LifecycleStatus lifecycleStatus);
    boolean existsBySchemaIdAndVersion(String schemaId, String version);
    List<SchemaVersion> findAllByLifecycleStatusOrderByTopicAscSchemaIdAscCreatedAtDesc(LifecycleStatus lifecycleStatus);
    List<SchemaVersion> findAllByOrderByTopicAscNameAscVersionAsc();
}
