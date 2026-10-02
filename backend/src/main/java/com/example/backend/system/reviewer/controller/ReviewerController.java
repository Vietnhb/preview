package com.example.backend.system.reviewer.controller;

import com.example.backend.system.problem.dto.AmbiguityContracts;
import com.example.backend.system.problem.dto.SpecificationResponse;
import com.example.backend.system.reviewer.service.ReviewerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviewer")
@RequiredArgsConstructor
public class ReviewerController {
    private final ReviewerService reviewerService;

    @GetMapping("/ambiguities")
    public List<AmbiguityContracts.Review> openAmbiguities() {
        return reviewerService.openAmbiguities();
    }

    @GetMapping("/ambiguities/page")
    public AmbiguityContracts.Page openAmbiguitiesPage(
            @RequestParam(required = false) String topic,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return reviewerService.openAmbiguitiesPage(topic, pageable);
    }

    @PostMapping("/ambiguities/{ambiguityId}/resolve")
    public SpecificationResponse resolve(@PathVariable UUID ambiguityId,
                                         @Valid @RequestBody AmbiguityContracts.Answer request) {
        return reviewerService.resolve(ambiguityId, request);
    }

    @PostMapping("/ambiguities/{ambiguityId}/claim")
    public AmbiguityContracts.Review claim(@PathVariable UUID ambiguityId) {
        return reviewerService.claim(ambiguityId);
    }

    @PostMapping("/ambiguities/{ambiguityId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID ambiguityId) {
        reviewerService.release(ambiguityId);
        return ResponseEntity.noContent().build();
    }

}
