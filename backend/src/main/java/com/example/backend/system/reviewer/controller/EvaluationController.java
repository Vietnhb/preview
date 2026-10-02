package com.example.backend.system.reviewer.controller;

import com.example.backend.system.reviewer.dto.EvaluationContracts;
import com.example.backend.system.reviewer.service.EvaluationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {
    private final EvaluationService evaluationService;

    @GetMapping("/history")
    public EvaluationContracts.RunPage history(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return evaluationService.history(status, pageable);
    }

    @GetMapping("/{id}")
    public EvaluationContracts.RunView detail(@PathVariable UUID id) {
        return evaluationService.detail(id);
    }

    @GetMapping("/compare")
    public EvaluationContracts.RunComparison compare(@RequestParam UUID baseline,
                                                    @RequestParam UUID candidate) {
        return evaluationService.compare(baseline, candidate);
    }
}
