package com.example.backend.repository;
import com.example.backend.enums.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, UUID> {
    List<SchemaVersion> findByLifecycleStatus(LifecycleStatus lifecycleStatus);
    Optional<SchemaVersion> findFirstBySchemaIdAndVersion(String schemaId, String version);
    List<SchemaVersion> findAllByOrderByTopicAscNameAscVersionAsc();
}
