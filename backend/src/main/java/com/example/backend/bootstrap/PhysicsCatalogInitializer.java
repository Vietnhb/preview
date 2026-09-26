package com.example.backend.bootstrap;

import java.io.InputStream;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** Publishes approved conceptual templates without numerical solver bindings. */
@Component
@ConditionalOnProperty(prefix = "physlive.bootstrap", name = "catalogs-enabled", havingValue = "true")
@RequiredArgsConstructor
public class PhysicsCatalogInitializer implements CommandLineRunner {
    private final SchemaVersionRepository schemaRepository;
    private final ObjectMapper objectMapper;
    private final SchemaDefinitionService schemaDefinitions;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        JsonNode activeCatalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            activeCatalog = objectMapper.readTree(input);
        }
        if (!activeCatalog.isArray() || activeCatalog.isEmpty()) {
            throw new IllegalStateException("Conceptual topic catalog must contain approved templates");
        }
        for (JsonNode entry : activeCatalog) upsert(entry);
    }

    private void upsert(JsonNode entry) {
        String id = entry.path("schemaId").asText();
        String version = entry.path("version").asText();
        JsonNode definition = entry.path("definition").deepCopy();
        if (!(definition instanceof ObjectNode object)) {
            throw new IllegalStateException("Catalog template has no definition: " + id + "@" + version);
        }
        object.put("model", entry.path("model").asText());
        schemaDefinitions.validateDefinition(definition, id, version, entry.path("topic").asText());
        String checksum = schemaDefinitions.compiledChecksum(definition);

        var published = schemaRepository.findFirstBySchemaIdAndVersion(id, version);
        if (published.isEmpty()) {
            SchemaVersion schema = new SchemaVersion();
            schema.setSchemaId(id);
            schema.setName(entry.path("name").asText());
            schema.setTopic(entry.path("topic").asText());
            schema.setVersion(version);
            schema.setDefinition(definition);
            schema.setDefinitionChecksum(checksum);
            schema.setLifecycleStatus(LifecycleStatus.APPROVED);
            schemaRepository.save(schema);
            return;
        }

        SchemaVersion existing = published.orElseThrow();
        existing.setName(entry.path("name").asText());
        existing.setTopic(entry.path("topic").asText());
        existing.setDefinition(definition);
        existing.setDefinitionChecksum(checksum);
        existing.setLifecycleStatus(LifecycleStatus.APPROVED);
        schemaRepository.save(existing);
    }
}
