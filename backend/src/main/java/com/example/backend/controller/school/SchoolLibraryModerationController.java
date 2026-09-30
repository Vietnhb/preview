package com.example.backend.controller.school;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.dto.reviewer.ModerateLibraryItemRequest;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.service.library.LibraryModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schools/{schoolId}/library")
@RequiredArgsConstructor
public class SchoolLibraryModerationController {
    private final LibraryModerationService service;
    @GetMapping
    public List<LibraryItemResponse> queue(@PathVariable UUID schoolId, @RequestParam(required = false) LibraryModerationStatus status) {
        return service.schoolQueue(schoolId, status);
    }
    @PutMapping("/{id}")
    public LibraryItemResponse moderate(@PathVariable UUID schoolId, @PathVariable UUID id, @Valid @RequestBody ModerateLibraryItemRequest request) {
        return service.moderateForSchool(schoolId, id, request.status(), request.comment());
    }
}
