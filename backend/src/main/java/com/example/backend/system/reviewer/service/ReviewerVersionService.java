package com.example.backend.system.reviewer.service;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.exception.ApiException;
import com.example.backend.system.physics.dto.SchemaContracts;
import com.example.backend.system.physics.dto.SolverContracts;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.physics.model.entity.SolverVersion;
import com.example.backend.system.physics.repository.SolverVersionRepository;
import com.example.backend.system.physics.service.SchemaDefinitionService;
import com.example.backend.system.physics.service.SchemaService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewerVersionService {
    private final SchemaService schemaService;
    private final SolverVersionRepository solverRepository;
    private final SchemaDefinitionService schemaDefinitions;

    @Transactional(readOnly = true)
    public List<SchemaVersion> schemas() {
        return schemaService.list(false);
    }

    public SchemaVersion updateSchema(UUID id, SchemaContracts.Request request) {
        return schemaService.updateDraft(id, request);
    }

    public SchemaVersion schemaLifecycle(UUID id, LifecycleStatus status) {
        return schemaService.changeVersionLifecycle(id, status);
    }

    public Map<String, List<String>> implementations() {
        List<SolverVersion> bindings = solverRepository.findAll();
        return Map.of(
                "numerical", bindings.stream().map(SolverVersion::getSolverId).filter(java.util.Objects::nonNull).distinct().sorted().toList(),
                "reference", bindings.stream().map(item -> item.getOutputDefinition().path("referenceSolverId").asText(""))
                        .filter(id -> !id.isBlank()).distinct().sorted().toList());
    }

    @Transactional(readOnly = true)
    public List<SchemaContracts.ModuleRelease> moduleReleases() {
        return schemaService.list(false).stream().map(SchemaContracts.ModuleRelease::from).toList();
    }

    public SchemaContracts.ModuleRelease moduleLifecycle(UUID id, LifecycleStatus status) {
        return SchemaContracts.ModuleRelease.from(schemaService.changeVersionLifecycle(id, status));
    }

    @Transactional(readOnly = true)
    public List<SolverVersion> solvers() {
        return solverRepository.findAll();
    }

    @Transactional
    public SolverVersion create(SolverContracts.Request request) {
        if (solverRepository.findFirstBySchemaIdAndVersion(request.schemaId(), request.version()).isPresent()) {
            throw ApiException.conflict("Phiên bản bộ giải này đã tồn tại. Hãy dùng số phiên bản khác.");
        }
        SolverVersion version = new SolverVersion();
        version.setSchemaId(request.schemaId());
        version.setVersion(request.version());
        return save(version, request);
    }

    @Transactional
    public SolverVersion update(UUID id, SolverContracts.Request request) {
        SolverVersion version = require(id);
        if (version.getLifecycleStatus() != LifecycleStatus.DRAFT) {
            throw ApiException.conflict("Chỉ sửa được bản nháp. Phiên bản đã duyệt cần tạo phiên bản mới.");
        }
        if (!version.getSchemaId().equals(request.schemaId()) || !version.getVersion().equals(request.version())) {
            throw ApiException.conflict("Không đổi được mã chủ đề hoặc số phiên bản của bản nháp.");
        }
        return save(version, request);
    }

    @Transactional
    public SolverVersion lifecycle(UUID id, LifecycleStatus status) {
        if (status == null) {
            throw ApiException.badRequest("Vui lòng chọn trạng thái.");
        }
        SolverVersion version = require(id);
        if (version.getLifecycleStatus() == status) {
            return version;
        }
        if (version.getLifecycleStatus() == LifecycleStatus.RETIRED || status == LifecycleStatus.DRAFT) {
            throw ApiException.conflict("Phiên bản đã phát hành không mở lại được. Hãy tạo phiên bản mới.");
        }
        if (status == LifecycleStatus.APPROVED) {
            validate(version.getSolverId(), version.getOutputDefinition());
            String checksum = schemaDefinitions.solverBindingChecksum(version.getSolverId(), version.getOutputDefinition());
            if (version.getBindingChecksum() != null && !version.getBindingChecksum().equals(checksum)) {
                throw ApiException.conflict("Bộ giải đã thay đổi so với lúc tạo bản nháp. Hãy tạo phiên bản mới.");
            }
            version.setBindingChecksum(checksum);
        }
        version.setLifecycleStatus(status);
        return solverRepository.save(version);
    }

    private SolverVersion save(SolverVersion version, SolverContracts.Request request) {
        validate(request.solverId(), request.outputDefinition());
        version.setSolverId(request.solverId());
        version.setOutputDefinition(request.outputDefinition());
        version.setBindingChecksum(schemaDefinitions.solverBindingChecksum(request.solverId(), request.outputDefinition()));
        return solverRepository.save(version);
    }

    private void validate(String solverId, JsonNode definition) {
        String referenceId = definition == null ? "" : definition.path("referenceSolverId").asText("");
        if (solverId == null || solverId.isBlank() || definition == null || !definition.isObject()) {
            throw ApiException.badRequest("Cần chọn bộ giải và nhập định nghĩa đầu ra hợp lệ.");
        }
    }

    private SolverVersion require(UUID id) {
        return solverRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên bản bộ giải."));
    }
}
