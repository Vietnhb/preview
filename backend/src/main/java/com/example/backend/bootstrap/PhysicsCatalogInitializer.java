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
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.module.PhysicsModuleRegistry;
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
    private final PhysicsModuleRegistry physicsModules;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        JsonNode activeCatalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            activeCatalog = objectMapper.readTree(input);
        }

        try (InputStream input = new ClassPathResource("schemas/history/published-versions.json").getInputStream()) {
            for (JsonNode entry : objectMapper.readTree(input)) {
                upsert(entry, LifecycleStatus.RETIRED);
            }
        }
        for (JsonNode entry : activeCatalog) {
            upsert(entry, LifecycleStatus.APPROVED);
        }
    }

    private void upsert(JsonNode entry, LifecycleStatus lifecycleStatus) {
        String id = entry.path("schemaId").asText();
        String version = entry.path("version").asText();
        var validatedDefinition = entry.path("definition").deepCopy();
        if (validatedDefinition instanceof com.fasterxml.jackson.databind.node.ObjectNode object)
            object.put("model", entry.path("model").asText());
        schemaDefinitions.validateDefinition(validatedDefinition, id, version, entry.path("topic").asText());
        String solverId = entry.path("solverId").asText();
        validateSolverBinding(solverId, entry.path("referenceSolverId").asText(),
                physicsModules, numericalSolvers, referenceSolvers);
        if (schemaRepository.findFirstBySchemaIdAndVersion(id, version).isEmpty()) {
            SchemaVersion schema = new SchemaVersion();
            schema.setSchemaId(id);
            schema.setName(entry.path("name").asText());
            schema.setTopic(entry.path("topic").asText());
            schema.setVersion(version);
            schema.setDefinition(validatedDefinition);
            schema.setDefinitionChecksum(schemaDefinitions.compiledChecksum(validatedDefinition));
            schema.setLifecycleStatus(lifecycleStatus);
            schemaRepository.save(schema);
        } else {
            SchemaVersion existing = schemaRepository.findFirstBySchemaIdAndVersion(id, version).orElseThrow();
            SchemaCatalogIntegrity.requireMetadataMatches(id, version, existing.getName(), existing.getTopic(),
                    entry.path("name").asText(), entry.path("topic").asText());
            SchemaCatalogIntegrity.requireDefinitionsMatch(id, version, existing.getDefinition(), validatedDefinition);
            String checksum = schemaDefinitions.compiledChecksum(validatedDefinition);
            // The structural equality check above is the guard. Once it has
            // passed, writing the deterministic checksum is metadata repair
            // only: PostgreSQL JSONB may reorder object keys and older builds
            // hashed the pre-storage representation. Published definition
            // bytes and lifecycle identity are never rewritten here.
            if (!checksum.equals(existing.getDefinitionChecksum())) {
                existing.setDefinitionChecksum(checksum);
                schemaRepository.save(existing);
            }
        }
        var binding = objectMapper.createObjectNode();
        binding.set("output", validatedDefinition.path("output").deepCopy());
        binding.put("referenceSolverId", entry.path("referenceSolverId").asText());
        String bindingChecksum = SchemaCatalogIntegrity.solverBindingChecksum(
                solverId, binding, schemaDefinitions::compiledChecksum);
        if (solverRepository.findFirstBySchemaIdAndVersion(id, version).isEmpty()) {
            SolverVersion solver = new SolverVersion();
            solver.setSchemaId(id);
            solver.setSolverId(solverId);
            solver.setVersion(version);
            solver.setLifecycleStatus(lifecycleStatus);
            solver.setOutputDefinition(binding);
            solver.setBindingChecksum(bindingChecksum);
            solverRepository.save(solver);
        } else {
            SolverVersion existing = solverRepository.findFirstBySchemaIdAndVersion(id, version).orElseThrow();
            String verifiedChecksum = SchemaCatalogIntegrity.checksumForVerifiedSolverBinding(id, version,
                    existing.getSolverId(), existing.getOutputDefinition(), solverId, binding,
                    schemaDefinitions::compiledChecksum);
            // checksumForVerifiedSolverBinding has already established that
            // the stored solver ID and output contract are structurally equal
            // to the checked-in catalog. Updating a legacy representation
            // checksum is therefore metadata repair, not a binding mutation.
            if (!verifiedChecksum.equals(existing.getBindingChecksum())) {
                existing.setBindingChecksum(verifiedChecksum);
                solverRepository.save(existing);
            }
        }
    }

    static void validateSolverBinding(String numericalSolverId, String referenceSolverId,
                                      PhysicsModuleRegistry physicsModules,
                                      PhysicsSolverRegistry numericalSolvers,
                                      ReferenceSolverRegistry referenceSolvers) {
        if (!physicsModules.supportsPair(numericalSolverId, referenceSolverId)) {
            numericalSolvers.get(numericalSolverId);
            referenceSolvers.get(referenceSolverId);
        }
    }
}
