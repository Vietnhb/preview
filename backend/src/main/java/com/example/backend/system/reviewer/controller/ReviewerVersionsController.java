package com.example.backend.system.reviewer.controller;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.dto.SchemaContracts;
import com.example.backend.system.physics.dto.SolverContracts;
import com.example.backend.system.reviewer.service.ReviewerVersionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviewer")
@RequiredArgsConstructor
public class ReviewerVersionsController {
    private final ReviewerVersionService service;

    @GetMapping("/schemas")
    public List<SchemaContracts.Response> schemas() {
        return service.schemas().stream().map(SchemaContracts.Response::from).toList();
    }

    @PutMapping("/schema-versions/{id}")
    public SchemaContracts.Response updateSchema(@PathVariable UUID id, @Valid @RequestBody SchemaContracts.Request request) {
        return SchemaContracts.Response.from(service.updateSchema(id, request));
    }

    @PutMapping("/schema-versions/{id}/lifecycle")
    public SchemaContracts.Response schemaLifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) {
        return SchemaContracts.Response.from(service.schemaLifecycle(id, status));
    }

    @GetMapping("/solver-implementations")
    public Map<String, List<String>> implementations() {
        return service.implementations();
    }

    @GetMapping("/solvers")
    public List<SolverContracts.Response> solvers() {
        return service.solvers().stream().map(SolverContracts.Response::from).toList();
    }

    @PostMapping("/solvers")
    public SolverContracts.Response create(@Valid @RequestBody SolverContracts.Request request) {
        return SolverContracts.Response.from(service.create(request));
    }

    @PutMapping("/solvers/{id}")
    public SolverContracts.Response update(@PathVariable UUID id,
                                @Valid @RequestBody SolverContracts.Request request) {
        return SolverContracts.Response.from(service.update(id, request));
    }

    @PutMapping("/solvers/{id}/lifecycle")
    public SolverContracts.Response lifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) {
        return SolverContracts.Response.from(service.lifecycle(id, status));
    }
}
