package com.example.backend.controller;

import com.example.backend.entity.*;
import com.example.backend.repository.SolverVersionRepository;
import com.example.backend.service.SchemaService;
import com.example.backend.dto.reviewer.SchemaRequest;
import com.example.backend.physics.*;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/reviewer") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_REVIEWER')")
public class ReviewerVersionsController {
    private final SchemaService schemas;
    private final SolverVersionRepository solvers;
    private final PhysicsSolverRegistry numerical;
    private final ReferenceSolverRegistry reference;
    public record SolverRequest(@NotBlank @Size(max=80) String schemaId, @NotBlank @Size(max=120) String solverId,
            @NotBlank @Size(max=16) String version, @NotNull JsonNode outputDefinition) { }

    @GetMapping("/schemas") public List<SchemaVersion> schemas() { return schemas.list(false); }
    @PutMapping("/schema-versions/{id}")
    public SchemaVersion updateSchema(@PathVariable UUID id, @Valid @RequestBody SchemaRequest request) { return schemas.updateDraft(id, request); }
    @PutMapping("/schema-versions/{id}/lifecycle")
    public SchemaVersion schemaLifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) { return schemas.changeVersionLifecycle(id, status); }
    @GetMapping("/solver-implementations")
    public Map<String, List<String>> implementations() { return Map.of("numerical", numerical.ids(), "reference", reference.ids()); }
    @GetMapping("/solvers") public List<SolverVersion> solvers() { return solvers.findAll(); }
    @PostMapping("/solvers") @Transactional
    public SolverVersion create(@Valid @RequestBody SolverRequest request) {
        if (solvers.findFirstBySchemaIdAndVersion(request.schemaId(), request.version()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Solver version already exists");
        SolverVersion version = new SolverVersion(); version.setSchemaId(request.schemaId()); version.setVersion(request.version());
        return save(version, request);
    }
    @PutMapping("/solvers/{id}") @Transactional
    public SolverVersion update(@PathVariable UUID id, @Valid @RequestBody SolverRequest request) {
        SolverVersion version = require(id);
        if (version.getLifecycleStatus() != LifecycleStatus.DRAFT) throw new ApiException(HttpStatus.CONFLICT, "Only drafts can be edited");
        if (!version.getSchemaId().equals(request.schemaId()) || !version.getVersion().equals(request.version()))
            throw new ApiException(HttpStatus.CONFLICT, "Version identity cannot be changed");
        return save(version, request);
    }
    private SolverVersion save(SolverVersion version, SolverRequest request) {
        validate(request.solverId(), request.outputDefinition());
        version.setSolverId(request.solverId()); version.setOutputDefinition(request.outputDefinition()); return solvers.save(version);
    }
    private void validate(String solverId, JsonNode definition) {
        if (!definition.isObject() || !numerical.ids().contains(solverId)
                || !reference.ids().contains(definition.path("referenceSolverId").asText()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose installed numerical and independent reference solver modules");
    }
    @PutMapping("/solvers/{id}/lifecycle") @Transactional
    public SolverVersion lifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) {
        SolverVersion version = require(id);
        if (version.getLifecycleStatus() == status) return version;
        if (version.getLifecycleStatus() == LifecycleStatus.RETIRED || status == LifecycleStatus.DRAFT)
            throw new ApiException(HttpStatus.CONFLICT, "Create a new version instead of reopening a published version");
        if (status == LifecycleStatus.APPROVED) validate(version.getSolverId(), version.getOutputDefinition());
        version.setLifecycleStatus(status); return solvers.save(version);
    }
    private SolverVersion require(UUID id) { return solvers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Solver version not found")); }
}
