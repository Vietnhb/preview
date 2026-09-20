package com.example.backend.service.problem;

import com.example.backend.dto.reviewer.SchemaRequest;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.problem.SchemaVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchemaService {
    private final SchemaVersionRepository schemaRepository;
    private final SchemaDefinitionService schemaDefinitions;
    private final com.example.backend.schema.routing.index.SchemaEmbeddingIndexer searchIndexer;

    @Transactional(readOnly = true)
    public List<SchemaVersion> list(boolean enabledOnly) {
        return enabledOnly
                ? schemaRepository.findAllByLifecycleStatusOrderByTopicAscSchemaIdAscCreatedAtDesc(LifecycleStatus.APPROVED)
                : schemaRepository.findAllByOrderByTopicAscNameAscVersionAsc();
    }

    @Transactional(readOnly = true)
    public SchemaVersion get(String schemaId) {
        return schemaRepository.findTopBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
                schemaId.trim(), LifecycleStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schema not found"));
    }

    @Transactional
    public SchemaVersion create(SchemaRequest request) {
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId(), request.version(), request.topic());
        if (schemaRepository.existsBySchemaIdAndVersion(request.schemaId().trim(), request.version().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "Schema version already exists");
        }
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(request.schemaId().trim());
        schema.setName(request.name().trim());
        schema.setTopic(request.topic().trim());
        schema.setVersion(request.version().trim());
        schema.setDefinition(request.definition());
        schema.setLifecycleStatus(LifecycleStatus.DRAFT);
        return schemaRepository.save(schema);
    }

    @Transactional
    public SchemaVersion changeLifecycle(String schemaId, LifecycleStatus status) {
        SchemaVersion schema = schemaRepository.findTopBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
                schemaId.trim(), LifecycleStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schema not found"));
        LifecycleStatus previousStatus = schema.getLifecycleStatus();
        transition(schema, status);
        SchemaVersion saved = schemaRepository.save(schema);
        if (previousStatus != status) searchIndexer.refreshAfterCatalogChange();
        return saved;
    }

    @Transactional
    public SchemaVersion changeVersionLifecycle(java.util.UUID id, LifecycleStatus status) {
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schema version not found"));
        LifecycleStatus previousStatus = schema.getLifecycleStatus();
        transition(schema, status);
        SchemaVersion saved = schemaRepository.save(schema);
        if (previousStatus != status) searchIndexer.refreshAfterCatalogChange();
        return saved;
    }

    private void transition(SchemaVersion schema, LifecycleStatus status) {
        if (schema.getLifecycleStatus() == status) return;
        if (schema.getLifecycleStatus() == LifecycleStatus.RETIRED || status == LifecycleStatus.DRAFT)
            throw new ApiException(HttpStatus.CONFLICT, "Create a new draft version instead of reopening a published version");
        if (status == LifecycleStatus.APPROVED) {
            schemaDefinitions.validateDefinition(schema.getDefinition(), schema.getSchemaId(), schema.getVersion(),
                    schema.getTopic());
            schemaDefinitions.requireSolverBinding(schema.getSchemaId(), schema.getVersion());
            String actualChecksum = schemaDefinitions.compiledChecksum(schema.getDefinition());
            if (schema.getDefinitionChecksum() != null && !schema.getDefinitionChecksum().equals(actualChecksum)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Schema checksum drift detected for " + schema.getSchemaId() + "@" + schema.getVersion()
                                + "; create a new schema version");
            }
            schema.setDefinitionChecksum(actualChecksum);
        }
        schema.setLifecycleStatus(status);
    }

    @Transactional
    public SchemaVersion updateDraft(java.util.UUID id, SchemaRequest request) {
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schema version not found"));
        if (schema.getLifecycleStatus() != LifecycleStatus.DRAFT) throw new ApiException(HttpStatus.CONFLICT, "Only drafts can be edited");
        if (!schema.getSchemaId().equals(request.schemaId()) || !schema.getVersion().equals(request.version()))
            throw new ApiException(HttpStatus.CONFLICT, "Schema/version identity cannot be changed");
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId(), request.version(), request.topic());
        schema.setName(request.name().trim()); schema.setTopic(request.topic().trim()); schema.setDefinition(request.definition());
        return schemaRepository.save(schema);
    }
}
