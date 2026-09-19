package com.example.backend.controller.reviewer;
import com.example.backend.service.evaluation.BenchmarkReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/reviewer/benchmarks") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_REVIEWER')")
public class BenchmarkReviewController {
    private final BenchmarkReviewService service;
    @GetMapping public List<BenchmarkReviewService.View> list() { return service.list(); }
    @PostMapping public BenchmarkReviewService.View create(@Valid @RequestBody BenchmarkReviewService.CreateRequest request) { return service.create(request); }
    @PostMapping("/{id}/annotations") public BenchmarkReviewService.View annotate(@PathVariable UUID id, @Valid @RequestBody BenchmarkReviewService.AnnotationRequest request) { return service.annotate(id, request); }
    @PostMapping("/{id}/adjudication") public BenchmarkReviewService.View adjudicate(@PathVariable UUID id, @Valid @RequestBody BenchmarkReviewService.AnnotationRequest request) { return service.adjudicate(id, request); }
}
