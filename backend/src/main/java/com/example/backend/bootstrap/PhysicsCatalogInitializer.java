package com.example.backend.bootstrap;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.physics.repository.SchemaVersionRepository;
import com.example.backend.system.physics.service.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes the curriculum-aligned topic schemas compiled from schemas/library + schemas/topics. */
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
        JsonNode activeCatalog = SchemaCatalogCompiler.loadFromClasspath(objectMapper);
        if (!activeCatalog.isArray() || activeCatalog.isEmpty()) {
            throw new IllegalStateException("Conceptual topic catalog must contain approved templates");
        }
        java.util.Set<String> active = new java.util.HashSet<>();
        for (JsonNode entry : activeCatalog) {
            upsert(entry);
            active.add(entry.path("schemaId").asText());
        }
        retireSuperseded(active);
    }

    /**
     * Schema ids listed in defaults.json "supersededSchemaIds" were replaced by the curriculum-aligned
     * library. They are retired (still replayable for saved runs) so routing only sees current topics.
     */
    private void retireSuperseded(java.util.Set<String> active) throws java.io.IOException {
        JsonNode defaults;
        try (InputStream input = new ClassPathResource(SchemaCatalogCompiler.LIBRARY + "defaults.json").getInputStream()) {
            defaults = objectMapper.readTree(input);
        }
        java.util.Set<String> superseded = new java.util.HashSet<>();
        defaults.path("supersededSchemaIds").forEach(id -> superseded.add(id.asText()));
        superseded.removeAll(active);
        for (SchemaVersion schema : schemaRepository.findByLifecycleStatus(LifecycleStatus.APPROVED)) {
            if (superseded.contains(schema.getSchemaId())) {
                schema.setLifecycleStatus(LifecycleStatus.RETIRED);
                schemaRepository.save(schema);
            }
        }
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
