package com.example.backend.controller.reviewer;
import com.example.backend.dto.evaluation.BenchmarkAdjudicationRequest;
import com.example.backend.dto.evaluation.BenchmarkAnnotationRequest;
import com.example.backend.dto.evaluation.BenchmarkCreateRequest;
import com.example.backend.dto.evaluation.BenchmarkUpdateRequest;
import com.example.backend.dto.reviewer.ArchiveBenchmarkRequest;
import com.example.backend.service.evaluation.BenchmarkReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/reviewer/benchmarks") @RequiredArgsConstructor
public class BenchmarkReviewController {
    private final BenchmarkReviewService service;

    @GetMapping public List<BenchmarkReviewService.View> list() { return service.list(); }

    @GetMapping("/page")
    public BenchmarkReviewService.PageView page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String gradeScope,
            @RequestParam(required = false) String sourceCategory,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.page(status, topic, gradeScope, sourceCategory, pageable);
    }

    @PostMapping public BenchmarkReviewService.View create(@Valid @RequestBody BenchmarkCreateRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public BenchmarkReviewService.View updateDraft(@PathVariable UUID id,
                                                   @Valid @RequestBody BenchmarkUpdateRequest request) {
        return service.updateDraft(id, request);
    }

    @PostMapping("/{id}/activate")
    public BenchmarkReviewService.View activate(@PathVariable UUID id) { return service.activate(id); }

    @PostMapping("/{id}/archive")
    public BenchmarkReviewService.View archive(@PathVariable UUID id, @Valid @RequestBody ArchiveBenchmarkRequest request) {
        return service.archive(id, request.reason());
    }

    @PostMapping("/{id}/annotations") public BenchmarkReviewService.View annotate(@PathVariable UUID id, @Valid @RequestBody BenchmarkAnnotationRequest request) { return service.annotate(id, request); }
    @PostMapping("/{id}/adjudication") public BenchmarkReviewService.View adjudicate(@PathVariable UUID id, @Valid @RequestBody BenchmarkAdjudicationRequest request) { return service.adjudicate(id, request); }
}
