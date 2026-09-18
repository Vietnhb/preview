package com.example.backend.controller;

import com.example.backend.entity.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.entity.TopicModuleRelease;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.SchemaVersionRepository;
import com.example.backend.repository.TopicModuleReleaseRepository;
import com.example.backend.service.SchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAnyRole('CONTENT_REVIEWER','ADMIN')")
public class ReviewerModuleController {
    private final TopicModuleReleaseRepository releases;
    private final SchemaVersionRepository schemas;
    private final SchemaService schemaService;

    @GetMapping
    public List<TopicModuleRelease> list() {
        return releases.findAllByOrderByTopicAscModuleNameAscSchemaVersionAsc();
    }

    @PutMapping("/{id}/lifecycle")
    public TopicModuleRelease lifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) {
        TopicModuleRelease release = releases.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module release not found"));
        SchemaVersion schema = schemas.findFirstBySchemaIdAndVersion(release.getSchemaId(), release.getSchemaVersion())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Module schema version is missing"));
        schemaService.changeVersionLifecycle(schema.getId(), status);
        return releases.findById(id).orElse(release);
    }
}
