package com.example.backend.controller;

import com.example.backend.entity.School;
import com.example.backend.repository.SchoolRepository;
import com.example.backend.repository.ValidationRunRepository;
import com.example.backend.service.CurriculumService;
import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.Instant;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsController {
    private final SchoolRepository schools;
    private final ValidationRunRepository validations;
    private final CurriculumService curriculum;
    public record SchoolRequest(@NotBlank @Size(max=80) String code, @NotBlank @Size(max=200) String name,
                                @Size(max=300) String address, boolean active) { }
    public record ValidationRow(UUID id, UUID submissionId, String topic, String schemaId, String schemaVersion,
            String solverVersion, boolean passed, String status, String errorMessage, Instant createdAt) { }

    @GetMapping("/schools") public List<School> schools() { return schools.findAll(); }
    @PostMapping("/schools") @Transactional
    public School create(@Valid @RequestBody SchoolRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (schools.existsByCode(code)) throw new ApiException(HttpStatus.CONFLICT, "School code already exists");
        School school = new School(); school.setCode(code); return save(school, request);
    }
    @PutMapping("/schools/{id}") @Transactional
    public School update(@PathVariable UUID id, @Valid @RequestBody SchoolRequest request) {
        School school = schools.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
        if (!school.getCode().equals(request.code().trim().toUpperCase(Locale.ROOT)))
            throw new ApiException(HttpStatus.CONFLICT, "School code cannot be changed");
        return save(school, request);
    }
    private School save(School school, SchoolRequest request) {
        school.setName(request.name().trim()); school.setAddress(request.address()); school.setActive(request.active());
        return schools.save(school);
    }
    @GetMapping("/curriculum") public CurriculumTreeResponse curriculum() { return curriculum.getTree(true); }
    @GetMapping("/validation-runs") @Transactional(readOnly=true)
    public List<ValidationRow> validationRuns() {
        return validations.findAll(org.springframework.data.domain.Sort.by("createdAt").descending()).stream().map(v -> {
            var sim = v.getSimulation();
            var spec = sim.getSpecification();
            return new ValidationRow(v.getId(), spec.getSubmission().getId(), spec.getTopic(), sim.getSchemaId(),
                    spec.getSchemaVersion(), sim.getSolverVersion(), v.isPassed(), v.getStatus(), v.getErrorMessage(), v.getCreatedAt());
        }).toList();
    }
}
