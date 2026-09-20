package com.example.backend.bootstrap;

import java.io.InputStream;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.physics.solver.PhysicsSolverRegistry;
import com.example.backend.physics.reference.ReferenceSolverRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(prefix = "physlive.bootstrap", name = "catalogs-enabled", havingValue = "true")
@RequiredArgsConstructor
public class PhysicsCatalogInitializer implements CommandLineRunner {
    private final SchemaVersionRepository schemaRepository;
    private final SolverVersionRepository solverRepository;
    private final ObjectMapper objectMapper;
    private final SchemaDefinitionService schemaDefinitions;
    private final PhysicsSolverRegistry numericalSolvers;
    private final ReferenceSolverRegistry referenceSolvers;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            for (JsonNode entry : objectMapper.readTree(input))
                upsert(entry);
        }
    }

    private void upsert(JsonNode entry) {
        String id = entry.path("schemaId").asText();
        String version = entry.path("version").asText();
        var validatedDefinition = entry.path("definition").deepCopy();
        if (validatedDefinition instanceof com.fasterxml.jackson.databind.node.ObjectNode object)
            object.put("model", entry.path("model").asText());
        schemaDefinitions.validateDefinition(validatedDefinition, id);
        numericalSolvers.get(entry.path("solverId").asText());
        referenceSolvers.get(entry.path("referenceSolverId").asText());
        if (schemaRepository.findFirstBySchemaIdAndVersion(id, version).isEmpty()) {
            SchemaVersion schema = new SchemaVersion();
            schema.setSchemaId(id);
            schema.setName(entry.path("name").asText());
            schema.setTopic(entry.path("topic").asText());
            schema.setVersion(version);
            schema.setDefinition(validatedDefinition);
            schema.setDefinitionChecksum(schemaDefinitions.compiledChecksum(validatedDefinition));
            schema.setLifecycleStatus(LifecycleStatus.APPROVED);
            schemaRepository.save(schema);
        } else {
            SchemaVersion existing = schemaRepository.findFirstBySchemaIdAndVersion(id, version).orElseThrow();
            String checksum = schemaDefinitions.compiledChecksum(validatedDefinition);
            if (existing.getDefinitionChecksum() != null && !existing.getDefinitionChecksum().equals(checksum)) {
                throw new IllegalStateException("Catalog drift for published schema " + id + "@" + version
                        + ": create a new schema version instead of mutating the published definition");
            }
            if (existing.getDefinitionChecksum() == null) {
                existing.setDefinitionChecksum(checksum);
                schemaRepository.save(existing);
            }
        }
        if (solverRepository.findFirstBySchemaIdAndVersion(id, version).isEmpty()) {
            SolverVersion solver = new SolverVersion();
            solver.setSchemaId(id);
            solver.setSolverId(entry.path("solverId").asText());
            solver.setVersion(version);
            solver.setLifecycleStatus(LifecycleStatus.APPROVED);
            var binding = objectMapper.createObjectNode();
            binding.set("output", entry.path("definition").path("output"));
            binding.put("referenceSolverId", entry.path("referenceSolverId").asText());
            solver.setOutputDefinition(binding);
            solverRepository.save(solver);
        }
    }
}
