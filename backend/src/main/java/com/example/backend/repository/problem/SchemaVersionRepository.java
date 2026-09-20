package com.example.backend.repository.problem;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, UUID> {
    List<SchemaVersion> findByLifecycleStatus(LifecycleStatus lifecycleStatus);
    List<SchemaVersion> findAllByLifecycleStatusAndTopicInOrderByTopicAscSchemaIdAscCreatedAtDesc(
            LifecycleStatus lifecycleStatus, List<String> topics);
    List<SchemaVersion> findAllBySchemaIdIgnoreCaseAndLifecycleStatusAndTopicInOrderByCreatedAtDesc(
            String schemaId, LifecycleStatus lifecycleStatus, List<String> topics);
    Optional<SchemaVersion> findFirstBySchemaIdAndVersion(String schemaId, String version);
    Optional<SchemaVersion> findTopBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
            String schemaId, LifecycleStatus lifecycleStatus);
    List<SchemaVersion> findAllBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
            String schemaId, LifecycleStatus lifecycleStatus);
    boolean existsBySchemaIdAndVersion(String schemaId, String version);
    List<SchemaVersion> findAllByLifecycleStatusOrderByTopicAscSchemaIdAscCreatedAtDesc(LifecycleStatus lifecycleStatus);
    List<SchemaVersion> findAllByOrderByTopicAscNameAscVersionAsc();
}
