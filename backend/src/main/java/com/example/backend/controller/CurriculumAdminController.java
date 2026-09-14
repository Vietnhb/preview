package com.example.backend.controller;

import com.example.backend.dto.curriculum.CurriculumNodeRequest;
import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.service.CurriculumAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/curriculum")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CurriculumAdminController {
    private final CurriculumAdminService service;

    @PostMapping("/topics")
    public CurriculumTreeResponse topic(@Valid @RequestBody CurriculumNodeRequest request) {
        return service.createTopic(request);
    }

    @PostMapping("/topics/{topicId}/modules")
    public CurriculumTreeResponse module(@PathVariable UUID topicId, @Valid @RequestBody CurriculumNodeRequest request) {
        return service.createModule(topicId, request);
    }

    @PostMapping("/modules/{moduleId}/levels")
    public CurriculumTreeResponse level(@PathVariable UUID moduleId, @Valid @RequestBody CurriculumNodeRequest request) {
        return service.createLevel(moduleId, request);
    }

    @PostMapping("/levels/{levelId}/lessons")
    public CurriculumTreeResponse lesson(@PathVariable UUID levelId, @Valid @RequestBody CurriculumNodeRequest request) {
        return service.createLesson(levelId, request);
    }

    @PutMapping("/{type}/{id}/toggle")
    public CurriculumTreeResponse toggle(@PathVariable String type, @PathVariable UUID id) {
        return service.toggle(type, id);
    }
}
