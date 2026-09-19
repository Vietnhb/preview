package com.example.backend.config;

import java.io.InputStream;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.enums.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.entity.SolverVersion;
import com.example.backend.repository.SchemaVersionRepository;
import com.example.backend.repository.SolverVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@Order(10)
@RequiredArgsConstructor
public class SchemaSeedRunner implements CommandLineRunner {
    private final SchemaVersionRepository schemaRepository;
    private final SolverVersionRepository solverRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            for (JsonNode entry : objectMapper.readTree(input)) upsert(entry);
        }
    }

    private void upsert(JsonNode entry) {
        String id = entry.path("schemaId").asText();
        String version = entry.path("version").asText();
        if (schemaRepository.findFirstBySchemaIdAndVersion(id, version).isEmpty()) {
            SchemaVersion schema = new SchemaVersion();
            schema.setSchemaId(id); schema.setName(entry.path("name").asText()); schema.setTopic(entry.path("topic").asText());
            schema.setVersion(version);
            var definition = entry.path("definition").deepCopy();
            if (definition instanceof com.fasterxml.jackson.databind.node.ObjectNode object) object.put("model", entry.path("model").asText());
            schema.setDefinition(definition);
            schema.setLifecycleStatus(LifecycleStatus.APPROVED); schemaRepository.save(schema);
        }
        if (solverRepository.findFirstBySchemaIdAndVersion(id, version).isEmpty()) {
            SolverVersion solver = new SolverVersion(); solver.setSchemaId(id);
            solver.setSolverId(entry.path("solverId").asText()); solver.setVersion(version);
            solver.setLifecycleStatus(LifecycleStatus.APPROVED);
            var binding = objectMapper.createObjectNode();
            binding.set("output", entry.path("definition").path("output"));
            binding.put("referenceSolverId", entry.path("referenceSolverId").asText());
            solver.setOutputDefinition(binding); solverRepository.save(solver);
        }
    }
}
