package com.example.backend.system.operations.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse;
import com.example.backend.system.curriculum.model.entity.Topic;
import com.example.backend.system.curriculum.repository.TopicRepository;
import com.example.backend.system.curriculum.service.CurriculumService;
import com.example.backend.system.operations.dto.OperationsContracts.PlanRequest;
import com.example.backend.system.operations.dto.OperationsContracts.TopicStatus;
import com.example.backend.system.operations.dto.OperationsContracts.ValidationMetrics;
import com.example.backend.system.operations.dto.OperationsContracts.ValidationRow;
import com.example.backend.system.operations.dto.OperationsContracts;
import com.example.backend.system.school.dto.SchoolContracts;
import com.example.backend.system.school.dto.SchoolPaymentContracts.LicensePlanResponse;
import com.example.backend.system.school.dto.SchoolPaymentContracts;
import com.example.backend.system.school.model.entity.LicensePlan;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.repository.LicensePlanRepository;
import com.example.backend.system.school.repository.SchoolRepository;
import com.example.backend.system.simulation.repository.SimulationRunRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOperationsService {
    private final SchoolRepository schoolRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final CurriculumService curriculumService;
    private final LicensePlanRepository planRepository;
    private final TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public List<LicensePlanResponse> plans() {
        return planRepository.findAll(Sort.by("annualPriceVnd")).stream().map(LicensePlanResponse::from).toList();
    }

    @Transactional
    public LicensePlanResponse createPlan(PlanRequest request) {
        if (planRepository.existsById(request.code())) {
            throw ApiException.conflict("Mã gói đã tồn tại.");
        }
        LicensePlan plan = new LicensePlan();
        plan.setCode(request.code());
        return savePlan(plan, request);
    }

    @Transactional
    public LicensePlanResponse updatePlan(String code, PlanRequest request) {
        if (!code.equals(request.code())) {
            throw ApiException.conflict("Không thể thay đổi mã gói.");
        }
        LicensePlan plan = planRepository.findById(code)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói."));
        return savePlan(plan, request);
    }

    @Transactional(readOnly = true)
    public List<SchoolContracts.Response> schools() {
        return schoolRepository.findAll().stream().map(SchoolContracts.Response::from).toList();
    }

    @Transactional
    public SchoolContracts.Response createSchool(SchoolContracts.Save request) {
        String code = normalizeCode(request.code());
        String name = request.name().trim();
        if (schoolRepository.existsByCode(code)) {
            throw ApiException.conflict("School code already exists");
        }
        if (schoolRepository.findByName(name).isPresent()) {
            throw ApiException.conflict("School name already exists");
        }
        School school = new School();
        school.setCode(code);
        return saveSchool(school, request);
    }

    @Transactional
    public SchoolContracts.Response updateSchool(UUID id, SchoolContracts.Save request) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("School not found"));
        if (!school.getCode().equals(normalizeCode(request.code()))) {
            throw ApiException.conflict("School code cannot be changed");
        }
        schoolRepository.findByName(request.name().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("School name already exists");
                });
        return saveSchool(school, request);
    }

    @Transactional(readOnly = true)
    public CurriculumTreeResponse curriculum() {
        return curriculumService.getTree(true);
    }

    @Transactional
    public TopicStatus toggleTopic(UUID id) {
        var topic = topicRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Topic not found"));
        topic.setEnabled(!topic.isEnabled());
        topicRepository.save(topic);
        return new TopicStatus(topic.getId(), topic.getName(), topic.isEnabled());
    }

    @Transactional(readOnly = true)
    public ValidationMetrics validationMetrics() {
        long total = simulationRunRepository.count();
        long failed = simulationRunRepository.countByValidationPassedFalse();
        return new ValidationMetrics(total, failed, total == 0 ? 0 : (double) failed / total);
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

    private LicensePlanResponse savePlan(LicensePlan plan, PlanRequest request) {
        plan.setName(request.name().trim());
        plan.setDescription(request.description().trim());
        plan.setAnnualPriceVnd(request.annualPriceVnd());
        plan.setStudentQuota(request.studentQuota());
        plan.setMonthlyTokenQuota(request.monthlyTokenQuota());
        plan.setActive(request.active());
        return LicensePlanResponse.from(planRepository.save(plan));
    }

    private SchoolContracts.Response saveSchool(School school, SchoolContracts.Save request) {
        if ((request.licenseStart() == null) != (request.licenseEnd() == null)
                || (request.licenseStart() != null && request.licenseEnd().isBefore(request.licenseStart()))) {
            throw ApiException.badRequest("Provide a valid license date range");
        }
        school.setLicenseStart(request.licenseStart());
        school.setLicenseEnd(request.licenseEnd());
        school.setMonthlyTokenQuota(request.monthlyTokenQuota());
        school.setName(request.name().trim());
        school.setAddress(request.address());
        school.setActive(request.active());
        return SchoolContracts.Response.from(schoolRepository.save(school));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
