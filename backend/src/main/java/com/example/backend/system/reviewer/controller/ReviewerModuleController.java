package com.example.backend.system.reviewer.controller;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.dto.SchemaContracts;
import com.example.backend.system.reviewer.service.ReviewerVersionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviewer/module-releases")
@RequiredArgsConstructor
public class ReviewerModuleController {
    private final ReviewerVersionService service;

    @GetMapping
    public List<SchemaContracts.ModuleRelease> list() {
        return service.moduleReleases();
    }

    @PutMapping("/{id}/lifecycle")
    public SchemaContracts.ModuleRelease lifecycle(@PathVariable UUID id,
                                                               @RequestParam LifecycleStatus status) {
        return service.moduleLifecycle(id, status);
    }
}
