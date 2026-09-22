package com.example.backend.simulation.assets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import com.example.backend.ai.extraction.model.PhysicalObject;
import com.example.backend.ai.extraction.model.SpecificationDocument;
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
import com.fasterxml.jackson.databind.node.ObjectNode;

class AssetSelectionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SvgAssetCatalog catalog = new SvgAssetCatalog(mapper, new DefaultResourceLoader());
    private final SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
    private final SpecificationRepository repository = mock(SpecificationRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final User owner = new User();
    private AssetSelectionService service;
    private JsonNode visual;
    private final String text = "A blue block moves. A red car follows it.";

    @BeforeEach
    void setUp() throws Exception {
        visual = mapper.readTree("""
            {"presentation":{"actors":[{"id":"first","asset":"vehicle.sport.blue","x":"positions.first"},
            {"id":"second","asset":"object.cart.orange","x":"positions.second"}],
            "effects":["motion.trail","vehicle.headlight"],"props":["launcher.cannon"]}}
            """);
        var schema = new SchemaVersion();
        schema.setDefinition(mapper.createObjectNode().set("visualization", visual));
        when(schemas.requireCurrentApproved("arbitrary-model", "7")).thenReturn(schema);
        when(schemas.visualization(schema.getDefinition())).thenAnswer(ignored -> visual.deepCopy());
        when(users.requireCurrentUser()).thenReturn(owner);
        var properties = new AssetSelectionProperties(.9);
        service = new AssetSelectionService(catalog, schemas, properties, mapper, repository, users,
                mock(ProblemResponseMapper.class));
    }

    @Test
    void selectionKeepsPhysicsBindingsAndRemovesUnmentionedApparatus() {
        JsonNode plan = service.create(document("EXACT"), route("EXACT"), text);
        assertEquals("READY", plan.path("status").asText());
        assertEquals("A blue block", plan.at("/choices/0/entityLabel").asText(), "teacher sees source wording, not internal IDs");
        assertEquals("block-cyan", plan.at("/visualization/presentation/actors/0/assetHint").asText());
        assertEquals("sport-red", plan.at("/visualization/presentation/actors/1/assetHint").asText());
        assertEquals("positions.first", plan.at("/visualization/presentation/actors/0/x").asText());
        assertEquals(mapper.valueToTree(List.of("motion.trail")), plan.at("/visualization/presentation/actors/0/effects"));
        assertEquals(mapper.valueToTree(List.of("motion.trail", "vehicle.headlight")), plan.at("/visualization/presentation/actors/1/effects"));
        assertTrue(plan.at("/visualization/presentation/props").isEmpty());
        assertFalse(plan.at("/visualization/presentation/actors/0").has("asset"));
        assertEquals("vehicle.sport.blue", visual.at("/presentation/actors/0/asset").asText(), "published schema stays immutable");
    }

    @Test
    void eitherGlobalOrEntitySpecificApproximationRequiresConsent() {
        assertEquals("NEEDS_CONFIRMATION", service.create(document("EXACT"), route("SUBSTITUTE"), text).path("status").asText());
        assertEquals("NEEDS_CONFIRMATION", service.create(document("SUBSTITUTE"), route("EXACT"), text).path("status").asText());
        var uncertain = new AssetRoutingDecision(catalog.checksum(), List.of(
                new AssetRoutingDecision.Candidate("block-cyan", "EXACT", .6),
                new AssetRoutingDecision.Candidate("sport-red", "EXACT", .99)));
        assertEquals("NEEDS_CONFIRMATION", service.create(document("EXACT"), uncertain, text).path("status").asText());
    }

    @Test
    void arbitraryIdsMissingTargetsAndForeignEntitiesCannotReachTheRenderer() {
        assertThrows(IllegalArgumentException.class, () -> service.create(document("EXACT"),
                new AssetRoutingDecision(catalog.checksum(), List.of()), text));
        var bindings = document("EXACT").visualBindings();
        var missing = copy(bindings.subList(0, 1));
        assertThrows(IllegalArgumentException.class, () -> service.create(missing, route("EXACT"), text));
        var foreign = copy(List.of(new VisualBinding(bindings.get(0).targetId(), "invented", "block-cyan", "EXACT", text),
                bindings.get(1), bindings.get(2)));
        assertThrows(IllegalArgumentException.class, () -> service.create(foreign, route("EXACT"), text));
    }

    @Test
    void noSuitableSvgProducesUnsupportedWithoutReusingSchemaAsset() {
        var bindings = document("EXACT").visualBindings();
        var unsupported = copy(List.of(new VisualBinding(bindings.get(0).targetId(), "a", null, "UNSUPPORTED", text),
                bindings.get(1), bindings.get(2)));
        JsonNode plan = service.create(unsupported, route("EXACT"), text);
        assertEquals("UNSUPPORTED", plan.path("status").asText());
        assertThrows(ApiException.class, () -> AssetSelectionService.requireReady(specification(plan)));
    }

    @Test
    void anExtraPhysicalObjectCannotSilentlyDisappearFromAFixedScene() {
        var base = document("EXACT");
        var objects = new java.util.ArrayList<>(base.objects());
        objects.add(new PhysicalObject("third", "Another body", "body"));
        var extra = new SpecificationDocument(base.schemaVersion(), base.topic(), base.schemaId(), objects,
                base.quantities(), base.relations(), base.endCondition(), base.confidence(), base.ambiguities(),
                base.contractVersion(), base.visualBindings());
        JsonNode plan = service.create(extra, route("EXACT"), text);
        assertEquals("UNSUPPORTED", plan.path("status").asText());
        assertEquals("third", plan.path("choices").get(2).path("entityId").asText());
    }

    @Test
    void rejectingPersistsTerminalDecisionAndBlocksDirectExecution() {
        var specification = specification(service.create(document("SUBSTITUTE"), route("EXACT"), text));
        UUID id = UUID.fromString(specification.getAssetSelection().path("id").asText());
        assertThrows(ApiException.class, () -> AssetSelectionService.requireReady(specification));
        service.decide(specification.getId(), new AssetSelectionService.Decision(id, false));
        assertEquals("REJECTED", specification.getAssetSelection().path("status").asText());
        assertThrows(ApiException.class, () -> AssetSelectionService.requireReady(specification));
        assertDoesNotThrow(() -> service.decide(specification.getId(), new AssetSelectionService.Decision(id, false)));
        assertThrows(ApiException.class, () -> service.decide(specification.getId(), new AssetSelectionService.Decision(id, true)));
    }

    @Test
    void approvalIsOwnedIdempotentAndInvalidatedByChangedText() {
        var specification = specification(service.create(document("SUBSTITUTE"), route("EXACT"), text));
        UUID id = UUID.fromString(specification.getAssetSelection().path("id").asText());
        assertThrows(ApiException.class, () -> service.decide(UUID.randomUUID(), new AssetSelectionService.Decision(id, true)));
        assertThrows(ApiException.class, () -> service.decide(specification.getId(), new AssetSelectionService.Decision(UUID.randomUUID(), true)));
        assertThrows(ApiException.class, () -> service.decide(specification.getId(), new AssetSelectionService.Decision(id, null)));
        service.decide(specification.getId(), new AssetSelectionService.Decision(id, true));
        service.decide(specification.getId(), new AssetSelectionService.Decision(id, true));
        assertDoesNotThrow(() -> AssetSelectionService.requireReady(specification));
        ((ObjectNode) specification.getObjects().get(0)).putArray("quantities").addObject().put("value", 12);
        assertDoesNotThrow(() -> AssetSelectionService.requireReady(specification), "numerical answers preserve visual choice");
        specification.getSubmission().setEditableText("A different physical object");
        assertThrows(ApiException.class, () -> AssetSelectionService.requireReady(specification));
    }

    @Test
    void propsAreRemovedByIdentityEvenWithManyAndNestedSlots() throws Exception {
        JsonNode scene = mapper.readTree("""
            {"presentation":{"sceneGraph":{"nodes":[{"id":"omitted","type":"prop"},
              {"id":"container","type":"rectangle","children":[{"id":"kept","type":"prop"}]}]}}}
            """);
        var targets = VisualTargets.read(scene);
        VisualTargets.assign(scene, targets.get(1), "resistor", List.of());
        VisualTargets.omit(scene, List.of(targets.get(0)));
        assertEquals("resistor", scene.at("/presentation/sceneGraph/nodes/0/children/0/properties/assetHint").asText());
        assertEquals(1, scene.at("/presentation/sceneGraph/nodes").size());
    }

    private Specification specification(JsonNode plan) {
        var specification = new Specification();
        specification.setId(UUID.randomUUID());
        specification.setSchemaId("arbitrary-model");
        specification.setSchemaVersion("7");
        specification.setObjects(mapper.valueToTree(document("EXACT").objects()));
        var submission = new ProblemSubmission();
        submission.setEditableText(text);
        specification.setSubmission(submission);
        specification.setAssetSelection(plan);
        when(repository.lockOwned(specification.getId(), owner)).thenReturn(Optional.of(specification));
        return specification;
    }

    private AssetRoutingDecision route(String match) {
        return new AssetRoutingDecision(catalog.checksum(), List.of(
                new AssetRoutingDecision.Candidate("block-cyan", match, .99),
                new AssetRoutingDecision.Candidate("sport-red", "EXACT", .99)));
    }

    private SpecificationDocument document(String match) {
        return copy(List.of(new VisualBinding("/presentation/actors/0", "a", "block-cyan", match, "A blue block"),
                new VisualBinding("/presentation/actors/1", "b", "sport-red", "EXACT", "A red car"),
                new VisualBinding("/presentation/props/0", null, null, "OMITTED", null)));
    }

    private SpecificationDocument copy(List<VisualBinding> bindings) {
        return new SpecificationDocument("7", "DYNAMICS", "arbitrary-model",
                List.of(new PhysicalObject("a", "Blue block", "body"), new PhysicalObject("b", "Red car", "body")),
                List.of(), List.of(), mapper.createObjectNode(), BigDecimal.ONE, List.of(), "1.0", bindings);
    }
}
