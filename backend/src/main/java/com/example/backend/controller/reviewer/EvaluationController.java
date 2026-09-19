package com.example.backend.controller.reviewer;

import com.example.backend.dto.evaluation.EvaluationResponse;
import com.example.backend.service.evaluation.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_REVIEWER')")
public class EvaluationController {
    private final EvaluationService evaluationService;

    @PostMapping("/run")
    public EvaluationResponse run() {
        return evaluationService.run();
    }
}
