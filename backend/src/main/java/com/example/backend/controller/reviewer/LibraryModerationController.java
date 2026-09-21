package com.example.backend.controller.reviewer;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.service.library.LibraryModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviewer/library")
@RequiredArgsConstructor
public class LibraryModerationController {
    private final LibraryModerationService service;
    public record ModerationRequest(LibraryModerationStatus status, @Size(max = 4000) String comment) { }

    @GetMapping
    public List<LibraryItemResponse> queue(@RequestParam(required = false) LibraryModerationStatus status) { return service.queue(status); }

    @GetMapping("/page")
    public LibraryModerationService.PageView page(@RequestParam(required = false) LibraryModerationStatus status,
                                                  @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.page(status, pageable);
    }

    @PutMapping("/{id}")
    public LibraryItemResponse moderate(@PathVariable UUID id, @Valid @RequestBody ModerationRequest request) {
        return service.moderate(id, request.status(), request.comment());
    }

    @GetMapping("/{id}/history")
    public List<LibraryModerationService.ModerationAuditView> history(@PathVariable UUID id) { return service.history(id); }
}
