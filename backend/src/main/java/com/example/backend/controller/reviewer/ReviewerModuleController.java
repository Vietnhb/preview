package com.example.backend.controller.reviewer;

import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.service.reviewer.ReviewerVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviewer/module-releases")
@RequiredArgsConstructor
public class ReviewerModuleController {
    private final ReviewerVersionService service;

    @GetMapping
    public List<ReviewerVersionService.ModuleReleaseView> list() {
        return service.moduleReleases();
    }

    @PutMapping("/{id}/lifecycle")
    public ReviewerVersionService.ModuleReleaseView lifecycle(@PathVariable UUID id,
                                                               @RequestParam LifecycleStatus status) {
        return service.moduleLifecycle(id, status);
    }
}
