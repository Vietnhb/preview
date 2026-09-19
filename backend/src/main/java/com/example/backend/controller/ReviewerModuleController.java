package com.example.backend.controller;

import com.example.backend.enums.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.repository.SchemaVersionRepository;
import com.example.backend.service.SchemaService;
import lombok.RequiredArgsConstructor;
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
    private final SchemaVersionRepository schemas;
    private final SchemaService schemaService;

    public record ModuleReleaseView(UUID id, String topic, String moduleName, String schemaId,
                                    String schemaVersion, LifecycleStatus lifecycleStatus) {
        static ModuleReleaseView from(SchemaVersion schema) {
            return new ModuleReleaseView(schema.getId(), schema.getTopic(), schema.getName(), schema.getSchemaId(),
                    schema.getVersion(), schema.getLifecycleStatus());
        }
    }

    @GetMapping
    public List<ModuleReleaseView> list() {
        return schemas.findAllByOrderByTopicAscNameAscVersionAsc().stream().map(ModuleReleaseView::from).toList();
    }

    @PutMapping("/{id}/lifecycle")
    public ModuleReleaseView lifecycle(@PathVariable UUID id, @RequestParam LifecycleStatus status) {
        return ModuleReleaseView.from(schemaService.changeVersionLifecycle(id, status));
    }
}
