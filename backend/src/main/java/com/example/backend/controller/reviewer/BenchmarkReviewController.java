package com.example.backend.controller.reviewer;
import com.example.backend.service.evaluation.BenchmarkReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/reviewer/benchmarks") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_REVIEWER')")
public class BenchmarkReviewController {
    private final BenchmarkReviewService service;

    public record ArchiveRequest(@NotBlank @Size(max = 4000) String reason) { }

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

    @PostMapping public BenchmarkReviewService.View create(@Valid @RequestBody BenchmarkReviewService.CreateRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public BenchmarkReviewService.View updateDraft(@PathVariable UUID id,
                                                   @Valid @RequestBody BenchmarkReviewService.UpdateRequest request) {
        return service.updateDraft(id, request);
    }

    @PostMapping("/{id}/activate")
    public BenchmarkReviewService.View activate(@PathVariable UUID id) { return service.activate(id); }

    @PostMapping("/{id}/archive")
    public BenchmarkReviewService.View archive(@PathVariable UUID id, @Valid @RequestBody ArchiveRequest request) {
        return service.archive(id, request.reason());
    }

    @PostMapping("/{id}/annotations") public BenchmarkReviewService.View annotate(@PathVariable UUID id, @Valid @RequestBody BenchmarkReviewService.AnnotationRequest request) { return service.annotate(id, request); }
    @PostMapping("/{id}/adjudication") public BenchmarkReviewService.View adjudicate(@PathVariable UUID id, @Valid @RequestBody BenchmarkReviewService.AdjudicationRequest request) { return service.adjudicate(id, request); }
}
