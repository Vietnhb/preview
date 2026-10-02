package com.example.backend.system.reviewer.service;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.dto.SchemaContracts;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.physics.service.SchemaService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewerVersionService {
    private final SchemaService schemaService;

    @Transactional(readOnly = true)
    public List<SchemaVersion> schemas() {
        return schemaService.list(false);
    }

    public SchemaVersion updateSchema(UUID id, SchemaContracts.Request request) {
        return schemaService.updateDraft(id, request);
    }

    public SchemaVersion schemaLifecycle(UUID id, LifecycleStatus status) {
        return schemaService.changeVersionLifecycle(id, status);
    }

    @Transactional(readOnly = true)
    public List<SchemaContracts.ModuleRelease> moduleReleases() {
        return schemaService.list(false).stream().map(SchemaContracts.ModuleRelease::from).toList();
    }

    public SchemaContracts.ModuleRelease moduleLifecycle(UUID id, LifecycleStatus status) {
        return SchemaContracts.ModuleRelease.from(schemaService.changeVersionLifecycle(id, status));
    }
}
