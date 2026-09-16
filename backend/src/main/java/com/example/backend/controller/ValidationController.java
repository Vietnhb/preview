package com.example.backend.controller;

import com.example.backend.dto.physics.ValidationRequest;
import com.example.backend.dto.physics.ValidationResponse;
import com.example.backend.physics.SolverOutput;
import com.example.backend.service.PhysicsValidationService;
import com.example.backend.service.SchemaDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/validations")
@RequiredArgsConstructor
public class ValidationController {
    private final SchemaDefinitionService schemaDefinitions;
    private final PhysicsValidationService validationService;

    @PostMapping
    public ValidationResponse validate(@Valid @RequestBody ValidationRequest request) {
        schemaDefinitions.requireSolverBinding(request.schemaId());
        SolverOutput numerical = new SolverOutput(request.simulation().time(), request.simulation().positions(),
                request.simulation().velocities(), request.simulation().accelerations(), request.simulation().values());
        return validationService.validateWithoutPersistence(request.specification(), request.schemaId(), numerical,
                request.adjustableParams() == null ? Map.of() : request.adjustableParams());
    }
}
