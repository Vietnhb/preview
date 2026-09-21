package com.example.backend.controller.reviewer;

import com.example.backend.dto.reviewer.SchemaRequest;
import com.example.backend.dto.reviewer.SolverRequest;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.service.reviewer.ReviewerVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviewer")
@RequiredArgsConstructor
public class ReviewerVersionsController {
    private final ReviewerVersionService service;

    @GetMapping("/schemas")
    public List<SchemaVersion> schemas() {
        return service.schemas();
    }

    @PutMapping("/schema-versions/{id}")
    public SchemaVersion updateSchema(@PathVariable UUID id, @Valid @RequestBody SchemaRequest request) {
        return service.updateSchema(id, request);
    }

    @PutMapping("/schema-versions/{id}/lifecycle")
    public SchemaVersion schemaLifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) {
        return service.schemaLifecycle(id, status);
    }

    @GetMapping("/solver-implementations")
    public Map<String, List<String>> implementations() {
        return service.implementations();
    }

    @GetMapping("/solvers")
    public List<SolverVersion> solvers() {
        return service.solvers();
    }

    @PostMapping("/solvers")
    public SolverVersion create(@Valid @RequestBody SolverRequest request) {
        return service.create(request);
    }

    @PutMapping("/solvers/{id}")
    public SolverVersion update(@PathVariable UUID id,
                                @Valid @RequestBody SolverRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/solvers/{id}/lifecycle")
    public SolverVersion lifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) {
        return service.lifecycle(id, status);
    }
}
