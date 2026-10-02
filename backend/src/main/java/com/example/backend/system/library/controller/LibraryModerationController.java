package com.example.backend.system.library.controller;

import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.library.dto.LibraryModeration;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.service.LibraryModerationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviewer/library")
@RequiredArgsConstructor
public class LibraryModerationController {
    private final LibraryModerationService service;
    @GetMapping
    public List<LibraryItemResponse> queue(@RequestParam(required = false) LibraryModerationStatus status) { return service.queue(status); }

    @GetMapping("/page")
    public LibraryModeration.Page page(@RequestParam(required = false) LibraryModerationStatus status,
                                                  @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.page(status, pageable);
    }

    @PutMapping("/{id}")
    public LibraryItemResponse moderate(@PathVariable UUID id, @Valid @RequestBody LibraryModeration.Request request) {
        return service.moderate(id, request.status(), request.comment());
    }

    @GetMapping("/{id}/history")
    public List<LibraryModeration.Audit> history(@PathVariable UUID id) { return service.history(id); }
}
