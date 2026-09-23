package com.example.backend.simulation.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;

import com.example.backend.config.properties.AssetSelectionProperties;
import com.example.backend.entity.account.User;
import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.Specification;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.problem.ProblemResponseMapper;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssetSelectionPolicyTest {
    private static final String SCHEMA_ID = "kinematics";
    private static final String SCHEMA_VERSION = "1.11";
    private static final String SOURCE = "A moving body.";

    private final ObjectMapper mapper = new ObjectMapper();
    private SvgAssetCatalog catalog;
    private SchemaDefinitionService schemas;
    private SpecificationRepository specifications;
    private CurrentUserService users;
    private ProblemResponseMapper responses;
    private AssetSelectionService service;
    private VisualTargets.Target actorTarget;
    private JsonNode confirmedObject;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new SvgAssetCatalog(mapper, new DefaultResourceLoader());
        schemas = mock(SchemaDefinitionService.class);
        specifications = mock(SpecificationRepository.class);
        users = mock(CurrentUserService.class);
        responses = mock(ProblemResponseMapper.class);

        JsonNode schemaEntry;
        try (InputStream input = getClass().getResourceAsStream("/schemas/catalog.json")) {
            JsonNode schemasJson = mapper.readTree(input);
            schemaEntry = java.util.stream.StreamSupport.stream(schemasJson.spliterator(), false)
                    .filter(item -> SCHEMA_ID.equals(item.path("schemaId").asText())
                            && SCHEMA_VERSION.equals(item.path("version").asText()))
                    .findFirst().orElseThrow();
        }
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(SCHEMA_ID);
        schema.setVersion(SCHEMA_VERSION);
        schema.setDefinition(schemaEntry.path("definition"));
        JsonNode visualization = schemaEntry.path("definition").path("visualization");
        when(schemas.requireCurrentApproved(SCHEMA_ID, SCHEMA_VERSION)).thenReturn(schema);
        when(schemas.visualization(schema.getDefinition())).thenReturn(visualization.deepCopy());
        actorTarget = VisualTargets.read(visualization).stream()
                .filter(target -> "actor".equals(target.kind())).findFirst().orElseThrow();
        confirmedObject = mapper.readTree("""
                {"id":"object-1","label":"Vật","type":"body","quantities":[]}
                """);
        service = new AssetSelectionService(catalog, schemas, new AssetSelectionProperties(0.8), mapper,
                specifications, users, responses);
    }

    @Test
    void exactCatalogMatchIsReadyWithoutTeacherDecision() {
        JsonNode plan = plan("cart-blue", "EXACT", "EXACT", 0.95);
        Specification persisted = persisted(plan);

        assertEquals("READY", plan.path("status").asText());
        assertEquals("cart-blue", AssetSelectionService.requireReady(persisted).path("presentation")
                .path("actors").path(0).path("assetHint").asText());
    }

    @Test
    void substituteRequiresExplicitAcceptanceAndAcceptedPlanBecomesReady() {
        JsonNode plan = plan("cart-blue", "SUBSTITUTE", "SUBSTITUTE", 0.95);
        Specification persisted = persisted(plan);
        UUID specificationId = UUID.randomUUID();
        User user = mock(User.class);
        when(users.requireCurrentUser()).thenReturn(user);
        when(specifications.lockOwned(eq(specificationId), eq(user))).thenReturn(Optional.of(persisted));

        assertEquals("NEEDS_CONFIRMATION", plan.path("status").asText());
        ApiException waiting = assertThrows(ApiException.class, () -> AssetSelectionService.requireReady(persisted));
        assertEquals(HttpStatus.CONFLICT, waiting.getStatus());

        service.decide(specificationId, new AssetSelectionService.Decision(
                UUID.fromString(plan.path("id").asText()), true));

        assertEquals("READY", persisted.getAssetSelection().path("status").asText());
        assertEquals("cart-blue", AssetSelectionService.requireReady(persisted).path("presentation")
                .path("actors").path(0).path("assetHint").asText());
    }

    @Test
    void rejectedSubstituteAndMissingCatalogMatchCannotRun() {
        JsonNode substitute = plan("cart-blue", "SUBSTITUTE", "SUBSTITUTE", 0.95);
        Specification rejected = persisted(substitute);
        UUID rejectedId = UUID.randomUUID();
        JsonNode originalObjects = rejected.getObjects().deepCopy();
        JsonNode originalQuantities = rejected.getQuantities().deepCopy();
        User user = mock(User.class);
        when(users.requireCurrentUser()).thenReturn(user);
        when(specifications.lockOwned(eq(rejectedId), eq(user))).thenReturn(Optional.of(rejected));

        service.decide(rejectedId, new AssetSelectionService.Decision(
                UUID.fromString(substitute.path("id").asText()), false));
        assertEquals("REJECTED", rejected.getAssetSelection().path("status").asText());
        assertEquals(originalObjects, rejected.getObjects(), "Rejecting an asset cannot alter physical objects.");
        assertEquals(originalQuantities, rejected.getQuantities(), "Rejecting an asset cannot alter physical quantities.");
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ApiException.class, () -> AssetSelectionService.requireReady(rejected)).getStatus());

        JsonNode unsupported = plan(null, "UNSUPPORTED", null, 0);
        assertEquals("UNSUPPORTED", unsupported.path("status").asText());
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ApiException.class, () -> AssetSelectionService.requireReady(persisted(unsupported)))
                        .getStatus());
    }

    @Test
    void rejectsAssetIdThatIsNotInTheBundledCatalog() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> plan("invented-asset", "EXACT", "EXACT", 0.99));

        assertTrue(failure.getMessage().contains("Unknown SVG asset ID"));
    }

    private JsonNode plan(String assetId, String bindingMatch, String catalogMatch, double confidence) {
        var binding = new VisualBinding(actorTarget.targetId(), "object-1", assetId, bindingMatch,
                "SUBSTITUTE".equals(bindingMatch) || "UNSUPPORTED".equals(bindingMatch)
                        ? "Catalog depiction differs from the described object." : null);
        List<AssetRoutingDecision.Candidate> candidates = assetId == null ? List.of()
                : List.of(new AssetRoutingDecision.Candidate(assetId, catalogMatch, confidence));
        var route = new AssetRoutingDecision(catalog.checksum(), candidates);
        var document = new com.example.backend.ai.extraction.model.SpecificationDocument(
                SCHEMA_VERSION, "KINEMATICS", SCHEMA_ID,
                List.of(new com.example.backend.ai.extraction.model.PhysicalObject(
                        "object-1", "Vật", "body", List.of())),
                List.of(), List.of(), mapper.createObjectNode().put("type", "time_limit").put("duration", 8),
                BigDecimal.ONE, List.of(),
                com.example.backend.ai.extraction.model.SpecificationDocument.CURRENT_SCHEMA_VERSION,
                List.of(binding));
        return service.create(document, route, SOURCE);
    }

    private Specification persisted(JsonNode plan) {
        Specification specification = new Specification();
        specification.setSchemaId(SCHEMA_ID);
        specification.setSchemaVersion(SCHEMA_VERSION);
        specification.setObjects(mapper.createArrayNode().add(confirmedObject.deepCopy()));
        specification.setQuantities(mapper.createArrayNode().addObject()
                .put("name", "initial_velocity").put("value", 10).put("originalUnit", "m/s")
                .put("normalizedValue", 10).put("normalizedUnit", "m/s"));
        ProblemSubmission submission = new ProblemSubmission();
        submission.setEditableText(SOURCE);
        specification.setSubmission(submission);
        specification.setAssetSelection(plan.deepCopy());
        return specification;
    }
}
