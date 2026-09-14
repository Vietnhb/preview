package com.example.backend.controller;

import com.example.backend.dto.problem.ResolveAmbiguityRequest;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.dto.reviewer.SchemaRequest;
import com.example.backend.entity.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.dto.reviewer.ReviewerAmbiguityResponse;
import com.example.backend.service.ReviewerService;
import com.example.backend.service.SchemaService;
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
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviewer")
@RequiredArgsConstructor
public class ReviewerController {
    private final ReviewerService reviewerService;
    private final SchemaService schemaService;

    @GetMapping("/ambiguities")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public List<ReviewerAmbiguityResponse> openAmbiguities() {
        return reviewerService.openAmbiguities();
    }

    @PostMapping("/ambiguities/{ambiguityId}/resolve")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public SpecificationResponse resolve(@PathVariable UUID ambiguityId,
                                         @Valid @RequestBody ResolveAmbiguityRequest request) {
        return reviewerService.resolve(ambiguityId, request);
    }

    @PostMapping("/schemas")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ResponseEntity<SchemaVersion> createSchema(@Valid @RequestBody SchemaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.create(request));
    }

    @PutMapping("/schemas/{schemaId}/lifecycle")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public SchemaVersion changeLifecycle(@PathVariable String schemaId, @RequestParam LifecycleStatus status) {
        return schemaService.changeLifecycle(schemaId, status);
    }
}
