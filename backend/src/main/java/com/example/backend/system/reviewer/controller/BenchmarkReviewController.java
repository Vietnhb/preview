package com.example.backend.system.reviewer.controller;

import com.example.backend.system.reviewer.dto.BenchmarkContracts;
import com.example.backend.system.reviewer.model.entity.Adjudication;
import com.example.backend.system.reviewer.service.BenchmarkReviewService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reviewer/benchmarks") @RequiredArgsConstructor
public class BenchmarkReviewController {
    private final BenchmarkReviewService service;

    @GetMapping public List<BenchmarkContracts.View> list() { return service.list(); }

    @GetMapping("/page")
    public BenchmarkContracts.PageView page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String gradeScope,
            @RequestParam(required = false) String sourceCategory,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.page(status, topic, gradeScope, sourceCategory, pageable);
    }

    @PostMapping public BenchmarkContracts.View create(@Valid @RequestBody BenchmarkContracts.Draft request) { return service.create(request); }

    @PutMapping("/{id}")
    public BenchmarkContracts.View updateDraft(@PathVariable UUID id,
                                                   @Valid @RequestBody BenchmarkContracts.Draft request) {
        return service.updateDraft(id, request);
    }

    @PostMapping("/{id}/activate")
    public BenchmarkContracts.View activate(@PathVariable UUID id) { return service.activate(id); }

    @PostMapping("/{id}/archive")
    public BenchmarkContracts.View archive(@PathVariable UUID id, @Valid @RequestBody BenchmarkContracts.Archive request) {
        return service.archive(id, request.reason());
    }

    @PostMapping("/{id}/annotations") public BenchmarkContracts.View annotate(@PathVariable UUID id, @Valid @RequestBody BenchmarkContracts.Annotation request) { return service.annotate(id, request); }
    @PostMapping("/{id}/adjudication") public BenchmarkContracts.View adjudicate(@PathVariable UUID id, @Valid @RequestBody BenchmarkContracts.Adjudication request) { return service.adjudicate(id, request); }
}
