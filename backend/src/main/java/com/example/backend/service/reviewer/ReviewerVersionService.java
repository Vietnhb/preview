package com.example.backend.service.reviewer;

import com.example.backend.dto.reviewer.SchemaRequest;
import com.example.backend.dto.reviewer.SolverRequest;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.SchemaService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewerVersionService {
    private final SchemaService schemaService;
    private final SolverVersionRepository solverRepository;
    private final PhysicsSolverRegistry numericalSolvers;
    private final ReferenceSolverRegistry referenceSolvers;
    private final SchemaDefinitionService schemaDefinitions;

    public record ModuleReleaseView(UUID id, String topic, String moduleName, String schemaId,
                                    String schemaVersion, LifecycleStatus lifecycleStatus) {
        static ModuleReleaseView from(SchemaVersion schema) {
            return new ModuleReleaseView(schema.getId(), schema.getTopic(), schema.getName(), schema.getSchemaId(),
                    schema.getVersion(), schema.getLifecycleStatus());
        }
    }

    @Transactional(readOnly = true)
    public List<SchemaVersion> schemas() {
        return schemaService.list(false);
    }

    public SchemaVersion updateSchema(UUID id, SchemaRequest request) {
        return schemaService.updateDraft(id, request);
    }

    public SchemaVersion schemaLifecycle(UUID id, LifecycleStatus status) {
        return schemaService.changeVersionLifecycle(id, status);
    }

    public Map<String, List<String>> implementations() {
        return Map.of("numerical", numericalSolvers.ids(), "reference", referenceSolvers.ids());
    }

    @Transactional(readOnly = true)
    public List<ModuleReleaseView> moduleReleases() {
        return schemaService.list(false).stream().map(ModuleReleaseView::from).toList();
    }

    public ModuleReleaseView moduleLifecycle(UUID id, LifecycleStatus status) {
        return ModuleReleaseView.from(schemaService.changeVersionLifecycle(id, status));
    }

    @Transactional(readOnly = true)
    public List<SolverVersion> solvers() {
        return solverRepository.findAll();
    }

    @Transactional
    public SolverVersion create(SolverRequest request) {
        if (solverRepository.findFirstBySchemaIdAndVersion(request.schemaId(), request.version()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Solver version already exists");
        }
        SolverVersion version = new SolverVersion();
        version.setSchemaId(request.schemaId());
        version.setVersion(request.version());
        return save(version, request);
    }

    @Transactional
    public SolverVersion update(UUID id, SolverRequest request) {
        SolverVersion version = require(id);
        if (version.getLifecycleStatus() != LifecycleStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only drafts can be edited");
        }
        if (!version.getSchemaId().equals(request.schemaId()) || !version.getVersion().equals(request.version())) {
            throw new ApiException(HttpStatus.CONFLICT, "Version identity cannot be changed");
        }
        return save(version, request);
    }

    @Transactional
    public SolverVersion lifecycle(UUID id, LifecycleStatus status) {
        if (status == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lifecycle status is required");
        }
        SolverVersion version = require(id);
        if (version.getLifecycleStatus() == status) {
            return version;
        }
        if (version.getLifecycleStatus() == LifecycleStatus.RETIRED || status == LifecycleStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Create a new version instead of reopening a published version");
        }
        if (status == LifecycleStatus.APPROVED) {
            validate(version.getSolverId(), version.getOutputDefinition());
            String checksum = schemaDefinitions.solverBindingChecksum(version.getSolverId(), version.getOutputDefinition());
            if (version.getBindingChecksum() != null && !version.getBindingChecksum().equals(checksum)) {
                throw new ApiException(HttpStatus.CONFLICT, "Solver binding checksum drift detected; create a new version");
            }
            version.setBindingChecksum(checksum);
        }
        version.setLifecycleStatus(status);
        return solverRepository.save(version);
    }

    private SolverVersion save(SolverVersion version, SolverRequest request) {
        validate(request.solverId(), request.outputDefinition());
        version.setSolverId(request.solverId());
        version.setOutputDefinition(request.outputDefinition());
        version.setBindingChecksum(schemaDefinitions.solverBindingChecksum(request.solverId(), request.outputDefinition()));
        return solverRepository.save(version);
    }

    private void validate(String solverId, JsonNode definition) {
        if (definition == null || !definition.isObject() || !numericalSolvers.ids().contains(solverId)
                || !referenceSolvers.ids().contains(definition.path("referenceSolverId").asText())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Choose installed numerical and independent reference solver modules");
        }
    }

    private SolverVersion require(UUID id) {
        return solverRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Solver version not found"));
    }
}
