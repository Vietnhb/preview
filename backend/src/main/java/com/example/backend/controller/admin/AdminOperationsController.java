package com.example.backend.controller.admin;

import com.example.backend.entity.school.LicensePlan;
import com.example.backend.repository.school.LicensePlanRepository;

import com.example.backend.entity.school.School;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.service.curriculum.CurriculumService;
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
    private final SimulationRunRepository simulationRuns;
    private final CurriculumService curriculum;
    private final com.example.backend.repository.school.LicensePlanRepository plans;
    public record PlanRequest(@NotBlank @Size(max=40) @Pattern(regexp="[A-Z0-9_-]+") String code,
        @NotBlank @Size(max=255) String name, @NotBlank @Size(max=255) String description,
        @NotNull @Positive Long annualPriceVnd, @NotNull @Positive Integer studentQuota,
        @PositiveOrZero Integer monthlyTokenQuota, boolean active) { }

    @GetMapping("/plans") public List<com.example.backend.entity.school.LicensePlan> plans() {
        return plans.findAll(org.springframework.data.domain.Sort.by("annualPriceVnd"));
    }
    @PostMapping("/plans") @Transactional
    public com.example.backend.entity.school.LicensePlan createPlan(@Valid @RequestBody PlanRequest request) {
        if (plans.existsById(request.code())) throw new ApiException(HttpStatus.CONFLICT, "Mã gói đã tồn tại.");
        var plan = new com.example.backend.entity.school.LicensePlan(); plan.setCode(request.code()); return savePlan(plan, request);
    }
    @PutMapping("/plans/{code}") @Transactional
    public com.example.backend.entity.school.LicensePlan updatePlan(@PathVariable String code, @Valid @RequestBody PlanRequest request) {
        if (!code.equals(request.code())) throw new ApiException(HttpStatus.CONFLICT, "Không thể thay đổi mã gói.");
        return savePlan(plans.findById(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy gói.")), request);
    }
    private com.example.backend.entity.school.LicensePlan savePlan(com.example.backend.entity.school.LicensePlan plan, PlanRequest request) {
        plan.setName(request.name().trim()); plan.setDescription(request.description().trim());
        plan.setAnnualPriceVnd(request.annualPriceVnd()); plan.setStudentQuota(request.studentQuota());
        plan.setMonthlyTokenQuota(request.monthlyTokenQuota()); plan.setActive(request.active()); return plans.save(plan);
    }
    public record SchoolRequest(@NotBlank @Size(max=80) String code, @NotBlank @Size(max=200) String name,
                                @Size(max=300) String address, boolean active,
                                java.time.LocalDate licenseStart, java.time.LocalDate licenseEnd,
                                @PositiveOrZero Integer monthlyTokenQuota) { }
    public record ValidationRow(UUID id, UUID submissionId, String topic, String schemaId, String schemaVersion,
            String solverVersion, boolean passed, String status, String errorMessage, Instant createdAt) { }

    @GetMapping("/schools") public List<School> schools() { return schools.findAll(); }
    @PostMapping("/schools") @Transactional
    public School create(@Valid @RequestBody SchoolRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (schools.existsByCode(code)) throw new ApiException(HttpStatus.CONFLICT, "School code already exists");
        if (schools.findByName(request.name().trim()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "School name already exists");
        School school = new School(); school.setCode(code); return save(school, request);
    }
    @PutMapping("/schools/{id}") @Transactional
    public School update(@PathVariable UUID id, @Valid @RequestBody SchoolRequest request) {
        School school = schools.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
        if (!school.getCode().equals(request.code().trim().toUpperCase(Locale.ROOT)))
            throw new ApiException(HttpStatus.CONFLICT, "School code cannot be changed");
        schools.findByName(request.name().trim()).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ApiException(HttpStatus.CONFLICT, "School name already exists"); });
        return save(school, request);
    }
    private School save(School school, SchoolRequest request) {
        if ((request.licenseStart() == null) != (request.licenseEnd() == null)
                || (request.licenseStart() != null && request.licenseEnd().isBefore(request.licenseStart())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide a valid license date range");
        school.setLicenseStart(request.licenseStart());
        school.setLicenseEnd(request.licenseEnd());
        school.setMonthlyTokenQuota(request.monthlyTokenQuota());
        school.setName(request.name().trim()); school.setAddress(request.address()); school.setActive(request.active());
        return schools.save(school);
    }
    @GetMapping("/curriculum") public CurriculumTreeResponse curriculum() { return curriculum.getTree(true); }
    @GetMapping("/validation-runs") @Transactional(readOnly=true)
    public List<ValidationRow> validationRuns() {
        return simulationRuns.findAll(org.springframework.data.domain.Sort.by("createdAt").descending()).stream().map(v -> {
            var sim = v.getSimulation();
            var spec = sim.getSpecification();
            return new ValidationRow(v.getId(), spec.getSubmission().getId(), spec.getTopic(), sim.getSchemaId(),
                    spec.getSchemaVersion(), sim.getSolverVersion(), v.isValidationPassed(),
                    v.isValidationPassed() ? "PASS" : "FAIL", v.getValidationError(), v.getCreatedAt());
        }).toList();
    }
}
