package com.example.backend.controller;

import com.example.backend.dto.evaluation.EvaluationResponse;
import com.example.backend.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
public class EvaluationController {
    private final EvaluationService evaluationService;

    @PostMapping("/run")
    public EvaluationResponse run() {
        return evaluationService.run();
    }
}
