package com.example.backend.system.problem.controller;

import com.example.backend.base.crud.dto.PageResponse;
import com.example.backend.system.problem.dto.ProblemResponse;
import com.example.backend.system.problem.dto.ProblemSummaryResponse;
import com.example.backend.system.problem.service.ProblemService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

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

    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<byte[]> downloadAsset(@PathVariable UUID assetId) {
        var asset = problemService.assetContent(assetId);
        MediaType mediaType = MediaType.parseMediaType(asset.contentType());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(asset.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(asset.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(asset.bytes());
    }

}
