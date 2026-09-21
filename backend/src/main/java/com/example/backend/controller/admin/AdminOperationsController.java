package com.example.backend.controller.admin;

import com.example.backend.dto.admin.PlanRequest;
import com.example.backend.dto.admin.SchoolRequest;
import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.entity.school.LicensePlan;
import com.example.backend.entity.school.School;
import com.example.backend.service.admin.AdminOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOperationsController {
    private final AdminOperationsService service;

    @GetMapping("/plans")
    public List<LicensePlan> plans() {
        return service.plans();
    }

    @PostMapping("/plans")
    public LicensePlan createPlan(@Valid @RequestBody PlanRequest request) {
        return service.createPlan(request);
    }

    @PutMapping("/plans/{code}")
    public LicensePlan updatePlan(@PathVariable String code,
                                  @Valid @RequestBody PlanRequest request) {
        return service.updatePlan(code, request);
    }

    @GetMapping("/schools")
    public List<School> schools() {
        return service.schools();
    }

    @PostMapping("/schools")
    public School createSchool(@Valid @RequestBody SchoolRequest request) {
        return service.createSchool(request);
    }

    @PutMapping("/schools/{id}")
    public School updateSchool(@PathVariable UUID id,
                               @Valid @RequestBody SchoolRequest request) {
        return service.updateSchool(id, request);
    }

    @GetMapping("/curriculum")
    public CurriculumTreeResponse curriculum() {
        return service.curriculum();
    }

    @GetMapping("/validation-runs")
    public List<AdminOperationsService.ValidationRow> validationRuns() {
        return service.validationRuns();
    }
}
