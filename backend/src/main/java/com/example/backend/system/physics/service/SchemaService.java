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
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mô hình"));
    }

    @Transactional
    public SchemaVersion create(SchemaContracts.Request request) {
        schemaDefinitions.validateTopicPackForAuthoring(request.definition(), request.schemaId());
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId(), request.version(), request.topic());
        if (schemaRepository.existsBySchemaIdAndVersion(request.schemaId().trim(), request.version().trim())) {
            throw ApiException.conflict("Phiên bản mô hình đã tồn tại");
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
        if (status == null) throw ApiException.badRequest("Vui lòng chọn trạng thái");
        SchemaVersion schema = schemaRepository.findTopBySchemaIdIgnoreCaseOrderByCreatedAtDesc(schemaId.trim())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mô hình"));
        transition(schema, status);
        return schemaRepository.save(schema);
    }

    @Transactional
    public SchemaVersion changeVersionLifecycle(java.util.UUID id, LifecycleStatus status) {
        if (status == null) throw ApiException.badRequest("Vui lòng chọn trạng thái");
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản mô hình"));
        transition(schema, status);
        return schemaRepository.save(schema);
    }

    private void transition(SchemaVersion schema, LifecycleStatus status) {
        if (schema.getLifecycleStatus() == status) return;
        if (schema.getLifecycleStatus() == LifecycleStatus.RETIRED || status == LifecycleStatus.DRAFT)
            throw ApiException.conflict("Vui lòng tạo bản nháp mới thay vì mở lại phiên bản đã xuất bản");
        if (schema.getLifecycleStatus() == LifecycleStatus.DRAFT && status != LifecycleStatus.APPROVED) {
            throw ApiException.conflict("Chỉ có thể phê duyệt bản nháp sau khi kiểm định minh chứng");
        }
        if (status == LifecycleStatus.APPROVED) {
            schemaDefinitions.validateDefinition(schema.getDefinition(), schema.getSchemaId(), schema.getVersion(),
                    schema.getTopic());
            String actualChecksum = schemaDefinitions.compiledChecksum(schema.getDefinition());
            if (schema.getDefinitionChecksum() != null && !schema.getDefinitionChecksum().equals(actualChecksum)) {
                throw ApiException.conflict("Phát hiện dữ liệu mô hình không nhất quán: " + schema.getSchemaId() + "@" + schema.getVersion()
                                + "; vui lòng tạo phiên bản mô hình mới");
            }
            schema.setDefinitionChecksum(actualChecksum);
        }
        schema.setLifecycleStatus(status);
    }

    @Transactional
    public SchemaVersion updateDraft(java.util.UUID id, SchemaContracts.Request request) {
        SchemaVersion schema = schemaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản mô hình"));
        if (schema.getLifecycleStatus() != LifecycleStatus.DRAFT) throw ApiException.conflict("Chỉ có thể chỉnh sửa bản nháp");
        if (!schema.getSchemaId().equals(request.schemaId()) || !schema.getVersion().equals(request.version()))
            throw ApiException.conflict("Không thể thay đổi định danh mô hình hoặc phiên bản");
        schemaDefinitions.validateTopicPackForAuthoring(request.definition(), request.schemaId());
        schemaDefinitions.validateDefinition(request.definition(), request.schemaId(), request.version(), request.topic());
        schema.setName(request.name().trim()); schema.setTopic(request.topic().trim()); schema.setDefinition(request.definition());
        return schemaRepository.save(schema);
    }
}
