package com.example.backend.system.physics.service;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.exception.ApiException;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.physics.dto.SchemaContracts;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.physics.repository.SchemaVersionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchemaService {
    private final SchemaVersionRepository schemaRepository;
    private final SchemaDefinitionService schemaDefinitions;
    private final AccountAccessService access;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<SchemaContracts.Response> listVisible(boolean enabledOnly) {
        boolean privileged = access.canEditContext(currentUser.requireCurrentUser());
        return list(enabledOnly).stream()
                .filter(schema -> privileged || schema.getLifecycleStatus() == LifecycleStatus.APPROVED)
                .map(SchemaContracts.Response::from).toList();
    }

    public com.fasterxml.jackson.databind.JsonNode topicPackMetaSchema() {
        return schemaDefinitions.topicPackMetaSchema();
    }

    public com.fasterxml.jackson.databind.JsonNode coreTypeLibrary() {
        return schemaDefinitions.coreTypeLibrary();
    }

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
                .orElseThrow(() -> ApiException.notFound("Schema not found"));
    }

    @Transactional
    public SchemaVersion create(SchemaContracts.Request request) {
        schemaDefinitions.validateTopicPackForAuthoring(request.definition(), request.schemaId());
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId(), request.version(), request.topic());
        if (schemaRepository.existsBySchemaIdAndVersion(request.schemaId().trim(), request.version().trim())) {
            throw ApiException.conflict("Schema version already exists");
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
        if (status == null) throw ApiException.badRequest("Lifecycle status is required");
        SchemaVersion schema = schemaRepository.findTopBySchemaIdIgnoreCaseOrderByCreatedAtDesc(schemaId.trim())
                .orElseThrow(() -> ApiException.notFound("Schema not found"));
        transition(schema, status);
        return schemaRepository.save(schema);
    }

    @Transactional
    public SchemaVersion changeVersionLifecycle(java.util.UUID id, LifecycleStatus status) {
        if (status == null) throw ApiException.badRequest("Lifecycle status is required");
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Schema version not found"));
        transition(schema, status);
        return schemaRepository.save(schema);
    }

    private void transition(SchemaVersion schema, LifecycleStatus status) {
        if (schema.getLifecycleStatus() == status) return;
        if (schema.getLifecycleStatus() == LifecycleStatus.RETIRED || status == LifecycleStatus.DRAFT)
            throw ApiException.conflict("Create a new draft version instead of reopening a published version");
        if (schema.getLifecycleStatus() == LifecycleStatus.DRAFT && status != LifecycleStatus.APPROVED) {
            throw ApiException.conflict("A draft can only move to APPROVED after evidence validation");
        }
        if (status == LifecycleStatus.APPROVED) {
            schemaDefinitions.validateDefinition(schema.getDefinition(), schema.getSchemaId(), schema.getVersion(),
                    schema.getTopic());
            if (!"2.0".equals(schema.getDefinition().path("metaSchemaVersion").asText())) {
                schemaDefinitions.requireSolverBinding(schema.getSchemaId(), schema.getVersion());
            }
            String actualChecksum = schemaDefinitions.compiledChecksum(schema.getDefinition());
            if (schema.getDefinitionChecksum() != null && !schema.getDefinitionChecksum().equals(actualChecksum)) {
                throw ApiException.conflict("Schema checksum drift detected for " + schema.getSchemaId() + "@" + schema.getVersion()
                                + "; create a new schema version");
            }
            schema.setDefinitionChecksum(actualChecksum);
        }
        schema.setLifecycleStatus(status);
    }

    @Transactional
    public SchemaVersion updateDraft(java.util.UUID id, SchemaContracts.Request request) {
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Schema version not found"));
        if (schema.getLifecycleStatus() != LifecycleStatus.DRAFT) throw ApiException.conflict("Only drafts can be edited");
        if (!schema.getSchemaId().equals(request.schemaId()) || !schema.getVersion().equals(request.version()))
            throw ApiException.conflict("Schema/version identity cannot be changed");
        schemaDefinitions.validateTopicPackForAuthoring(request.definition(), request.schemaId());
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId(), request.version(), request.topic());
        schema.setName(request.name().trim()); schema.setTopic(request.topic().trim()); schema.setDefinition(request.definition());
        return schemaRepository.save(schema);
    }
}
