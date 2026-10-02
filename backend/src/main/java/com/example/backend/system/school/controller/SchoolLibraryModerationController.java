package com.example.backend.system.school.controller;

import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.library.dto.LibraryModeration.Request;
import com.example.backend.system.library.dto.LibraryModeration;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.service.LibraryModerationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public LibraryItemResponse moderate(@PathVariable UUID schoolId, @PathVariable UUID id, @Valid @RequestBody Request request) {
        return service.moderateForSchool(schoolId, id, request.status(), request.comment());
    }
}
