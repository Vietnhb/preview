package com.example.backend.system.physics.controller;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.dto.SchemaContracts;
import com.example.backend.system.physics.service.SchemaService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/schemas")
@RequiredArgsConstructor
public class SchemaController {
    private final SchemaService schemaService;

    @GetMapping
    public List<SchemaContracts.Response> list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return schemaService.listVisible(enabledOnly);
    }

    @GetMapping("/meta-schema")
    public JsonNode topicPackMetaSchema() {
        return schemaService.topicPackMetaSchema();
    }

    @GetMapping("/core-types")
    public JsonNode coreTypeLibrary() {
        return schemaService.coreTypeLibrary();
    }

    @GetMapping("/{schemaId}")
    public SchemaContracts.Response get(@PathVariable String schemaId) {
        return SchemaContracts.Response.from(schemaService.get(schemaId));
    }

    @PostMapping
    public ResponseEntity<SchemaContracts.Response> create(@Valid @RequestBody SchemaContracts.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SchemaContracts.Response.from(schemaService.create(request)));
    }

    @PutMapping("/{schemaId}/lifecycle")
    public SchemaContracts.Response lifecycle(@PathVariable String schemaId, @RequestParam LifecycleStatus status) {
        return SchemaContracts.Response.from(schemaService.changeLifecycle(schemaId, status));
    }
}
