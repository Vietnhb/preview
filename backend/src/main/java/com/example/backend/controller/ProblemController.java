package com.example.backend.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.dto.problem.CreateProblemRequest;
import com.example.backend.dto.problem.ConfirmProblemRequest;
import com.example.backend.dto.problem.PageResponse;
import com.example.backend.dto.problem.ProblemResponse;
import com.example.backend.dto.problem.ProblemSummaryResponse;
import com.example.backend.dto.problem.UpdateProblemTextRequest;
import com.example.backend.dto.problem.UpdateSpecificationRequest;
import com.example.backend.entity.SourceAsset;
import com.example.backend.service.ProblemService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @PostMapping
    public ResponseEntity<ProblemResponse> create(@RequestBody CreateProblemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(problemService.create(request));
    }

    @PostMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProblemResponse> createFromImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) UUID lessonId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(problemService.createFromImage(file, text, lessonId));
    }

    @GetMapping
    public PageResponse<ProblemSummaryResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return problemService.history(page, size);
    }

    @GetMapping("/{id}")
    public ProblemResponse get(@PathVariable UUID id) {
        return problemService.get(id);
    }

    @PutMapping("/{id}/text")
    public ProblemResponse updateText(@PathVariable UUID id, @RequestBody UpdateProblemTextRequest request) {
        return problemService.updateText(id, request == null ? null : request.text());
    }

    @PostMapping("/{id}/extract")
    public ProblemResponse extract(@PathVariable UUID id) {
        return problemService.extract(id);
    }

    @PutMapping("/{id}/specification")
    public ProblemResponse updateSpecification(@PathVariable UUID id,
            @RequestBody UpdateSpecificationRequest request) {
        return problemService.updateSpecification(id, request);
    }

    @PostMapping("/{id}/confirm")
    public ProblemResponse confirm(@PathVariable UUID id, @RequestBody(required = false) ConfirmProblemRequest request) {
        return problemService.confirm(id, request == null ? null : request.answers());
    }

    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<byte[]> downloadAsset(@PathVariable UUID assetId) {
        SourceAsset asset = problemService.requireOwnedAsset(assetId);
        MediaType mediaType = MediaType.parseMediaType(asset.getContentType());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(asset.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(asset.getContentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(asset.getContent());
    }
}
