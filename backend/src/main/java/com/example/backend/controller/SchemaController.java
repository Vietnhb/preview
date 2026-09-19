package com.example.backend.controller;

import com.example.backend.dto.reviewer.SchemaRequest;
import com.example.backend.enums.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.service.SchemaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/schemas")
@RequiredArgsConstructor
public class SchemaController {
    private final SchemaService schemaService;

    @GetMapping
    public List<SchemaVersion> list(@RequestParam(defaultValue = "false") boolean enabledOnly,
                                   org.springframework.security.core.Authentication authentication) {
        boolean privileged = authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_CONTENT_REVIEWER"));
        return schemaService.list(enabledOnly).stream().filter(s -> privileged
                || s.getLifecycleStatus() == LifecycleStatus.APPROVED).toList();
    }

    @GetMapping("/{schemaId}")
    public SchemaVersion get(@PathVariable String schemaId) {
        return schemaService.get(schemaId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT_REVIEWER','ADMIN')")
    public ResponseEntity<SchemaVersion> create(@Valid @RequestBody SchemaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.create(request));
    }

    @PutMapping("/{schemaId}/lifecycle")
    @PreAuthorize("hasAnyRole('CONTENT_REVIEWER','ADMIN')")
    public SchemaVersion lifecycle(@PathVariable String schemaId, @RequestParam LifecycleStatus status) {
        return schemaService.changeLifecycle(schemaId, status);
    }
}
