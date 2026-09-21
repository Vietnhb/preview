package com.example.backend.controller.reviewer;

import com.example.backend.dto.evaluation.EvaluationResponse;
import com.example.backend.service.evaluation.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/history")
    public EvaluationService.RunPage history(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return evaluationService.history(status, pageable);
    }

    @GetMapping("/{id}")
    public EvaluationService.RunView detail(@PathVariable UUID id) {
        return evaluationService.detail(id);
    }

    @GetMapping("/compare")
    public EvaluationService.RunComparison compare(@RequestParam UUID baseline,
                                                    @RequestParam UUID candidate) {
        return evaluationService.compare(baseline, candidate);
    }
}
