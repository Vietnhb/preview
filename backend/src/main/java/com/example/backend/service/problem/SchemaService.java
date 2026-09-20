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
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId());
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
        transition(schema, status);
        return schemaRepository.save(schema);
    }

    @Transactional
    public SchemaVersion changeVersionLifecycle(java.util.UUID id, LifecycleStatus status) {
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schema version not found"));
        transition(schema, status);
        return schemaRepository.save(schema);
    }

    private void transition(SchemaVersion schema, LifecycleStatus status) {
        if (schema.getLifecycleStatus() == status) return;
        if (schema.getLifecycleStatus() == LifecycleStatus.RETIRED || status == LifecycleStatus.DRAFT)
            throw new ApiException(HttpStatus.CONFLICT, "Create a new draft version instead of reopening a published version");
        if (status == LifecycleStatus.APPROVED) {
            schemaDefinitions.validateDefinition(schema.getDefinition(), schema.getSchemaId());
            schemaDefinitions.requireSolverBinding(schema.getSchemaId(), schema.getVersion());
        }
        schema.setLifecycleStatus(status);
    }

    @Transactional
    public SchemaVersion updateDraft(java.util.UUID id, SchemaRequest request) {
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schema version not found"));
        if (schema.getLifecycleStatus() != LifecycleStatus.DRAFT) throw new ApiException(HttpStatus.CONFLICT, "Only drafts can be edited");
        if (!schema.getSchemaId().equals(request.schemaId()) || !schema.getVersion().equals(request.version()))
            throw new ApiException(HttpStatus.CONFLICT, "Schema/version identity cannot be changed");
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId());
        schema.setName(request.name().trim()); schema.setTopic(request.topic().trim()); schema.setDefinition(request.definition());
        return schemaRepository.save(schema);
    }
}
