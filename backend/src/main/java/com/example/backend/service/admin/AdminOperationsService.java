package com.example.backend.service.admin;

import com.example.backend.dto.admin.PlanRequest;
import com.example.backend.dto.admin.SchoolRequest;
import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.entity.school.LicensePlan;
import com.example.backend.entity.school.School;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.school.LicensePlanRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.service.curriculum.CurriculumService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOperationsService {
    private final SchoolRepository schoolRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final CurriculumService curriculumService;
    private final LicensePlanRepository planRepository;

    public record ValidationRow(UUID id, UUID submissionId, String topic, String schemaId, String schemaVersion,
                                String solverVersion, boolean passed, String status, String errorMessage,
                                Instant createdAt) { }

    @Transactional(readOnly = true)
    public List<LicensePlan> plans() {
        return planRepository.findAll(Sort.by("annualPriceVnd"));
    }

    @Transactional
    public LicensePlan createPlan(PlanRequest request) {
        if (planRepository.existsById(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Mã gói đã tồn tại.");
        }
        LicensePlan plan = new LicensePlan();
        plan.setCode(request.code());
        return savePlan(plan, request);
    }

    @Transactional
    public LicensePlan updatePlan(String code, PlanRequest request) {
        if (!code.equals(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Không thể thay đổi mã gói.");
        }
        LicensePlan plan = planRepository.findById(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy gói."));
        return savePlan(plan, request);
    }

    @Transactional(readOnly = true)
    public List<School> schools() {
        return schoolRepository.findAll();
    }

    @Transactional
    public School createSchool(SchoolRequest request) {
        String code = normalizeCode(request.code());
        String name = request.name().trim();
        if (schoolRepository.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "School code already exists");
        }
        if (schoolRepository.findByName(name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "School name already exists");
        }
        School school = new School();
        school.setCode(code);
        return saveSchool(school, request);
    }

    @Transactional
    public School updateSchool(UUID id, SchoolRequest request) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
        if (!school.getCode().equals(normalizeCode(request.code()))) {
            throw new ApiException(HttpStatus.CONFLICT, "School code cannot be changed");
        }
        schoolRepository.findByName(request.name().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "School name already exists");
                });
        return saveSchool(school, request);
    }

    @Transactional(readOnly = true)
    public CurriculumTreeResponse curriculum() {
        return curriculumService.getTree(true);
    }

    @Transactional(readOnly = true)
    public List<ValidationRow> validationRuns() {
        return simulationRunRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(run -> {
            var simulation = run.getSimulation();
            var specification = simulation.getSpecification();
            return new ValidationRow(run.getId(), specification.getSubmission().getId(), specification.getTopic(),
                    simulation.getSchemaId(), specification.getSchemaVersion(), simulation.getSolverVersion(),
                    run.isValidationPassed(), run.isValidationPassed() ? "PASS" : "FAIL", run.getValidationError(),
                    run.getCreatedAt());
        }).toList();
    }

    private LicensePlan savePlan(LicensePlan plan, PlanRequest request) {
        plan.setName(request.name().trim());
        plan.setDescription(request.description().trim());
        plan.setAnnualPriceVnd(request.annualPriceVnd());
        plan.setStudentQuota(request.studentQuota());
        plan.setMonthlyTokenQuota(request.monthlyTokenQuota());
        plan.setActive(request.active());
        return planRepository.save(plan);
    }

    private School saveSchool(School school, SchoolRequest request) {
        if ((request.licenseStart() == null) != (request.licenseEnd() == null)
                || (request.licenseStart() != null && request.licenseEnd().isBefore(request.licenseStart()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide a valid license date range");
        }
        school.setLicenseStart(request.licenseStart());
        school.setLicenseEnd(request.licenseEnd());
        school.setMonthlyTokenQuota(request.monthlyTokenQuota());
        school.setName(request.name().trim());
        school.setAddress(request.address());
        school.setActive(request.active());
        return schoolRepository.save(school);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
