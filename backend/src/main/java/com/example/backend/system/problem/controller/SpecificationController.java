package com.example.backend.system.problem.controller;

import com.example.backend.system.problem.dto.AmbiguityContracts;
import com.example.backend.system.problem.dto.SpecificationResponse;
import com.example.backend.system.problem.dto.ValidationReadinessResponse;
import com.example.backend.system.problem.service.SpecificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/specifications")
@RequiredArgsConstructor
public class SpecificationController {

    private final SpecificationService specificationService;

    @GetMapping("/{id}")
    public SpecificationResponse get(@PathVariable UUID id) {
        return specificationService.get(id);
    }

    @PostMapping("/{specificationId}/ambiguities/{ambiguityId}/confirm")
    public SpecificationResponse confirmAmbiguity(
            @PathVariable UUID specificationId,
            @PathVariable UUID ambiguityId,
            @RequestBody AmbiguityContracts.Answer request) {
        return specificationService.resolve(specificationId, ambiguityId, request);
    }

    @GetMapping("/{id}/validation-readiness")
    public ValidationReadinessResponse readiness(@PathVariable UUID id) {
        return specificationService.readiness(id);
    }
}
