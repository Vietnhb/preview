package com.example.backend.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.problem.ResolveAmbiguityRequest;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.dto.problem.ValidationReadinessResponse;
import com.example.backend.service.SpecificationService;

import lombok.RequiredArgsConstructor;

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
            @RequestBody ResolveAmbiguityRequest request) {
        return specificationService.resolve(specificationId, ambiguityId, request);
    }

    @GetMapping("/{id}/validation-readiness")
    public ValidationReadinessResponse readiness(@PathVariable UUID id) {
        return specificationService.readiness(id);
    }
}
