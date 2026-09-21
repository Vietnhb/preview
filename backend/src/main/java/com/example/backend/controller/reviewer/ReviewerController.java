package com.example.backend.controller.reviewer;
import com.example.backend.entity.enums.LifecycleStatus;

import com.example.backend.dto.problem.ResolveAmbiguityRequest;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.dto.reviewer.SchemaRequest;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.dto.reviewer.ReviewerAmbiguityResponse;
import com.example.backend.service.reviewer.ReviewerService;
import com.example.backend.service.problem.SchemaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviewer")
@RequiredArgsConstructor
public class ReviewerController {
    private final ReviewerService reviewerService;
    private final SchemaService schemaService;

    @GetMapping("/ambiguities")
    public List<ReviewerAmbiguityResponse> openAmbiguities() {
        return reviewerService.openAmbiguities();
    }

    @GetMapping("/ambiguities/page")
    public ReviewerService.AmbiguityPage openAmbiguitiesPage(
            @RequestParam(required = false) String topic,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return reviewerService.openAmbiguitiesPage(topic, pageable);
    }

    @PostMapping("/ambiguities/{ambiguityId}/resolve")
    public SpecificationResponse resolve(@PathVariable UUID ambiguityId,
                                         @Valid @RequestBody ResolveAmbiguityRequest request) {
        return reviewerService.resolve(ambiguityId, request);
    }

    @PostMapping("/ambiguities/{ambiguityId}/claim")
    public ReviewerAmbiguityResponse claim(@PathVariable UUID ambiguityId) {
        return reviewerService.claim(ambiguityId);
    }

    @PostMapping("/ambiguities/{ambiguityId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID ambiguityId) {
        reviewerService.release(ambiguityId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/schemas")
    public ResponseEntity<SchemaVersion> createSchema(@Valid @RequestBody SchemaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.create(request));
    }

    @PutMapping("/schemas/{schemaId}/lifecycle")
    public SchemaVersion changeLifecycle(@PathVariable String schemaId, @RequestParam LifecycleStatus status) {
        return schemaService.changeLifecycle(schemaId, status);
    }
}
