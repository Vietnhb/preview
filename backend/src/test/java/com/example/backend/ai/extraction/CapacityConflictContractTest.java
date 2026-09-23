package com.example.backend.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.prompt.ExtractionPromptBuilder;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.AiProviderProperties;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.SpecificationReadinessService;
import com.example.backend.service.problem.AmbiguityResolutionApplier;
import com.example.backend.service.school.SchoolService;
import com.example.backend.schema.routing.service.JevSchemaRoutingService;
import com.example.backend.simulation.assets.AssetSelectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Exercises the production prompt builder and strict validator for staged capacity handling. */
class CapacityConflictContractTest {
    private static final String PROBLEM =
            "2 vật chuyển động thẳng với vận tốc đầu 10 m/s và gia tốc 2 m/s². Hãy mô phỏng trong 8 giây.";
    private final ObjectMapper mapper = new ObjectMapper();
    private final CandidateContractProjection contract = contract();
    private final SchemaCandidate candidate = new SchemaCandidate(contract,
            List.of("JEV_ENTITY_COUNT:2"), 0.95);
    private final UnitNormalizer units = new UnitNormalizer(mapper);

    @Test
    void realPromptAndValidatorAllowMissingQuantitiesOnlyWhileCapacityConflictIsOpen() throws Exception {
        String system = new ClassPathResource("prompts/physics-specification-system.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        var prompt = new ExtractionPromptBuilder(system, 4, 20_000).build(List.of(contract), PROBLEM,
                List.of("issue=JEV_CAPACITY_EXCEEDED; fieldPath=" + CompatibilityFieldPaths.CAPACITY
                        + "; routedCount=2; actorCapacity=1"));
        assertTrue(prompt.systemMessage().contains(CompatibilityFieldPaths.CAPACITY));
        assertTrue(prompt.systemMessage().contains("do not ask for missing quantities yet"));
        assertTrue(prompt.systemMessage().contains("ask which one to retain"));
        assertFalse(prompt.systemMessage().contains("fieldPath=schemaId"));

        JsonNode initial = response(List.of(ambiguity(CompatibilityFieldPaths.CAPACITY,
                "jev.capacity.actor-count", "Bạn muốn giữ nguyên yêu cầu hay đồng ý rút gọn mô phỏng?")));
        StrictSpecificationValidator.validate(initial);
        assertDoesNotThrow(() -> StrictSpecificationValidator.validateCandidateMembership(initial,
                decision("JEV_CAPACITY_EXCEEDED", SchemaRoutingDecision.Status.AMBIGUOUS), units));

        JsonNode afterConsent = response(List.of(
                ambiguity(CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT, "jev.capacity.retained-object",
                        "Bạn muốn giữ vật nào trong mô phỏng?"),
                ambiguity("quantities.initial_position", "schema.required.initial_position",
                        "Vị trí ban đầu của vật được giữ là bao nhiêu mét?")));
        StrictSpecificationValidator.validate(afterConsent);
        assertDoesNotThrow(() -> StrictSpecificationValidator.validateCandidateMembership(afterConsent,
                decision("PINNED_FOR_AMBIGUITY_RESOLUTION", SchemaRoutingDecision.Status.SELECTED), units, true));

        JsonNode missingAfterResolution = response(List.of(), 1);
        assertThrows(IllegalArgumentException.class, () ->
                StrictSpecificationValidator.validateCandidateMembership(missingAfterResolution,
                        decision("PINNED_FOR_AMBIGUITY_RESOLUTION", SchemaRoutingDecision.Status.SELECTED), units));
    }

    @Test
    void structuredProviderUsesProductionPromptAndValidatorForTheTwoObjectRequest() throws Exception {
        String systemPrompt = new ClassPathResource("prompts/physics-specification-system.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        var properties = new AiProviderProperties("test", "test-key", URI.create("https://example.test"),
                "test-model", "test-vision", "low", 0, 1, true, Duration.ofSeconds(2),
                Duration.ofSeconds(2), 512, "classpath:prompts/physics-specification-system.txt",
                "classpath:prompts/physics-ocr-system.txt");
        var routingProperties = new JevProperties("", URI.create("https://example.test"), "test-jev",
                Duration.ofSeconds(2), 4, 0.5, 0.1, 2_000, 20_000);
        ChatCompletionClient client = spy(new ChatCompletionClient(RestClient.builder(), mapper, properties,
                mock(SchoolService.class), new DefaultResourceLoader()));
        JsonNode validResponse = response(List.of(ambiguity(CompatibilityFieldPaths.CAPACITY,
                "jev.capacity.actor-count", "Bạn muốn giữ nguyên hai vật hay đồng ý rút gọn mô phỏng?")));
        doReturn(new ChatCompletionClient.Completion("test-model", mapper.writeValueAsString(validResponse),
                mapper.createObjectNode())).when(client).completeStructured(anyString(), anyList());

        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(contract.schemaId());
        schema.setVersion(contract.schemaVersion());
        schema.setTopic(contract.topic());
        schema.setName(contract.name());
        schema.setDefinition(mapper.readTree("""
                {"model":"test-motion-model","requiredQuantities":[
                  {"key":"initial_position","aliases":["x0"],"allowedUnits":["m"]},
                  {"key":"initial_velocity","aliases":["v0"],"allowedUnits":["m/s"]},
                  {"key":"acceleration","aliases":["a"],"allowedUnits":["m/s²"]}],
                  "entityContract":{"types":[{"type":"body","min":1,"max":1}]},
                  "execution":{"durationSeconds":8}}
                """));
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.requireCurrentApproved(contract.schemaId(), contract.schemaVersion())).thenReturn(schema);
        when(schemas.canonicalQuantityKey(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenAnswer(call -> call.getArgument(1));
        StructuredExtractionProvider provider = new StructuredExtractionProvider(client, mapper, units, schemas,
                properties, routingProperties, new DefaultResourceLoader(), new SimpleMeterRegistry(),
                mock(AssetSelectionService.class));

        ProviderExtractionResult result = provider.extract(PROBLEM,
                decision("JEV_CAPACITY_EXCEEDED", SchemaRoutingDecision.Status.AMBIGUOUS), List.of(
                        "issue=JEV_CAPACITY_EXCEEDED; fieldPath=" + CompatibilityFieldPaths.CAPACITY
                                + "; routedCount=2; actorCapacity=1"));

        assertTrue(result.document().ambiguities().stream().anyMatch(item ->
                CompatibilityFieldPaths.CAPACITY.equals(item.fieldPath())));
        assertTrue(result.document().objects().size() == 2, "Both stated objects must survive initial extraction.");
        verify(client).completeStructured(anyString(), anyList());
    }

    @Test
    void productionRetryPromptKeepsCapacityConsentAheadOfRequiredInputs() throws Exception {
        JsonNode valid = response(List.of(ambiguity(CompatibilityFieldPaths.CAPACITY,
                "jev.capacity.actor-count", "Bạn có đồng ý rút gọn mô phỏng không?")));
        var provider = providerRetrying("not-json", valid);
        List<String> findings = List.of("issue=JEV_CAPACITY_EXCEEDED; fieldPath="
                + CompatibilityFieldPaths.CAPACITY + "; routedCount=2; actorCapacity=1");

        ProviderExtractionResult result = provider.provider().extract(PROBLEM,
                decision("JEV_CAPACITY_EXCEEDED", SchemaRoutingDecision.Status.AMBIGUOUS), findings);

        assertTrue(result.document().objects().size() == 2);
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(provider.client(), times(2)).completeStructured(anyString(), messages.capture());
        String retry = messages.getAllValues().get(1).toString();
        assertTrue(retry.contains("Defer missing quantities"));
        assertTrue(retry.contains(CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT));
    }

    @Test
    void realClarificationPromptKeepsBothObjectsAfterConsentAndAsksWhichOneToRetain() throws Exception {
        JsonNode accepted = response(List.of(ambiguity(
                CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT, "jev.capacity.retained-object",
                "Bạn muốn giữ vật nào?")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) accepted).withArray("resolutionDecisions")
                .add(decisionJson("jev.capacity.actor-count", "ACCEPT_SIMPLIFICATION", List.of()));
        var provider = providerReturning(accepted);
        JsonNode current = response(List.of(ambiguity(CompatibilityFieldPaths.CAPACITY,
                "jev.capacity.actor-count", "Bạn có đồng ý rút gọn mô phỏng không?")));

        ProviderExtractionResult result = provider.provider().resolveAmbiguities(PROBLEM, current,
                Map.of("jev.capacity.actor-count", "Đồng ý rút gọn"), List.of(
                        new ConversationTurn("user", PROBLEM),
                        new ConversationTurn("assistant", "Bạn có đồng ý rút gọn mô phỏng không?"),
                        new ConversationTurn("user", "Đồng ý rút gọn")));

        assertTrue(result.document().objects().size() == 2,
                "Consent to reduce capacity must not let the model choose an object to discard.");
        assertTrue(result.document().ambiguities().stream().anyMatch(item ->
                CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT.equals(item.fieldPath())));
        assertFalse(result.document().ambiguities().stream().anyMatch(item ->
                item.fieldPath().startsWith("quantities.")), "Do not ask for quantities before the retained object is chosen.");
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(provider.client(), times(1)).completeStructured(anyString(), messages.capture());
        assertTrue(messages.getValue().toString().contains(CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT));
        assertTrue(messages.getValue().toString().contains("keep all objects"));
    }

    @Test
    void realValidatorRequiresMissingQuantityAfterRetainedObjectIsSelected() throws Exception {
        var output = response(List.of(ambiguity("quantities.initial_position", "schema.required.initial_position",
                "Vị trí ban đầu của vật được giữ là bao nhiêu mét?")), 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) output).withArray("resolutionDecisions")
                .add(decisionJson("jev.capacity.retained-object", "ANSWERED", List.of("body-2")));
        var provider = providerReturning(output);
        JsonNode current = response(List.of(ambiguity(CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT,
                "jev.capacity.retained-object", "Bạn muốn giữ vật nào?")));

        ProviderExtractionResult result = provider.provider().resolveAmbiguities(PROBLEM, current,
                Map.of("jev.capacity.retained-object", "Giữ vật 1"), List.of(
                        new ConversationTurn("user", "Đồng ý rút gọn mô phỏng"),
                        new ConversationTurn("assistant", "Bạn muốn giữ vật nào?"),
                        new ConversationTurn("user", "Giữ vật 1")));

        assertTrue(result.document().objects().size() == 1);
        assertTrue(result.document().ambiguities().stream().anyMatch(item ->
                "quantities.initial_position".equals(item.fieldPath())));
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validateCandidateMembership(
                response(List.of(), 1), decision("PINNED_FOR_AMBIGUITY_RESOLUTION",
                        SchemaRoutingDecision.Status.SELECTED), units));
    }

    @Test
    void backendApplierDefersReductionAfterConsentUntilTheUserIdentifiesAnObject() throws Exception {
        JsonNode accepted = response(List.of(ambiguity(CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT,
                "jev.capacity.retained-object", "Bạn muốn giữ vật nào?")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) accepted).withArray("resolutionDecisions")
                .add(decisionJson("jev.capacity.actor-count", "ACCEPT_SIMPLIFICATION", List.of()));
        var fixture = providerReturning(accepted);
        var routing = mock(JevSchemaRoutingService.class);
        var readiness = mock(SpecificationReadinessService.class);
        var assetSelections = mock(AssetSelectionService.class);
        var applierSchemas = mock(SchemaDefinitionService.class);
        when(applierSchemas.requireCurrentApproved(contract.schemaId(), contract.schemaVersion()))
                .thenReturn(approvedTestSchema());
        when(applierSchemas.canonicalizeQuantities(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));
        var applier = new AmbiguityResolutionApplier(mapper, fixture.provider(), applierSchemas, readiness,
                routing, assetSelections);
        Specification specification = openCapacitySpecification();

        applier.applyAll(specification, Map.of("jev.capacity.actor-count", "Đồng ý rút gọn"), List.of(
                new ConversationTurn("user", PROBLEM),
                new ConversationTurn("assistant", "Bạn có đồng ý rút gọn mô phỏng không?"),
                new ConversationTurn("user", "Đồng ý rút gọn")));

        assertEquals(2, specification.getObjects().size(), "Consent must not silently remove either stated object.");
        assertTrue(specification.getAmbiguityCases().stream().anyMatch(item ->
                item.getStatus() == AmbiguityStatus.OPEN
                        && CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT.equals(item.getFieldPath())));
        assertEquals(ConfirmationState.UNRESOLVED, specification.getConfirmationState());
        verifyNoInteractions(routing, assetSelections);
    }

    @Test
    void backendApplierStopsAfterTheUserDeclinesCompatibility() throws Exception {
        JsonNode declined = response(List.of());
        ((com.fasterxml.jackson.databind.node.ObjectNode) declined).withArray("resolutionDecisions")
                .add(decisionJson("jev.capacity.actor-count", "DECLINE_SIMPLIFICATION", List.of()));
        var fixture = providerReturning(declined);
        var routing = mock(JevSchemaRoutingService.class);
        var readiness = mock(SpecificationReadinessService.class);
        var assetSelections = mock(AssetSelectionService.class);
        var applier = new AmbiguityResolutionApplier(mapper, fixture.provider(), mock(SchemaDefinitionService.class),
                readiness, routing, assetSelections);
        Specification specification = openCapacitySpecification();

        applier.applyAll(specification, Map.of("jev.capacity.actor-count", "Không, tôi sẽ sửa yêu cầu"), List.of(
                new ConversationTurn("user", PROBLEM),
                new ConversationTurn("assistant", "Bạn có đồng ý rút gọn mô phỏng không?"),
                new ConversationTurn("user", "Không, tôi sẽ sửa yêu cầu")));

        assertEquals(ConfirmationState.REJECTED, specification.getConfirmationState());
        assertEquals(AmbiguityStatus.REJECTED, specification.getAmbiguityCases().getFirst().getStatus());
        assertEquals(0, specification.getAmbiguity().size());
        verifyNoInteractions(readiness, routing, assetSelections);
    }

    private JsonNode response(List<Map<String, Object>> ambiguities) throws Exception {
        return response(ambiguities, 2);
    }

    private JsonNode response(List<Map<String, Object>> ambiguities, int objectCount) throws Exception {
        var root = mapper.createObjectNode();
        root.put("contractVersion", "1.0");
        root.put("schemaVersion", contract.schemaVersion());
        root.put("topic", contract.topic());
        root.put("schemaId", contract.schemaId());
        var objects = root.putArray("objects");
        for (int index = 1; index <= objectCount; index++) {
            var object = objects.addObject();
            object.put("id", "body-" + index);
            object.put("label", "Vật " + index);
            object.put("type", "body");
            object.putArray("quantities");
        }
        var quantities = root.putArray("quantities");
        quantity(quantities, "initial_velocity", "v0", 10, "m/s");
        quantity(quantities, "acceleration", "a", 2, "m/s²");
        root.putArray("relations");
        root.set("endCondition", mapper.readTree("{\"type\":\"time_limit\",\"duration\":8}"));
        root.put("confidence", 0.9);
        root.set("ambiguities", mapper.valueToTree(ambiguities));
        root.putArray("visualBindings");
        root.putArray("resolutionDecisions");
        return root;
    }

    private JsonNode decisionJson(String code, String outcome, List<String> omittedObjectIds) {
        return mapper.valueToTree(Map.of("code", code, "outcome", outcome, "omittedObjectIds", omittedObjectIds));
    }

    private ProviderFixture providerReturning(JsonNode response) throws Exception {
        return providerWithResponses(List.of(mapper.writeValueAsString(response)));
    }

    private ProviderFixture providerRetrying(String firstResponse, JsonNode retryResponse) throws Exception {
        return providerWithResponses(List.of(firstResponse, mapper.writeValueAsString(retryResponse)));
    }

    private ProviderFixture providerWithResponses(List<String> responseContents) throws Exception {
        var properties = new AiProviderProperties("test", "test-key", URI.create("https://example.test"),
                "test-model", "test-vision", "low", 0, responseContents.size(), true, Duration.ofSeconds(2),
                Duration.ofSeconds(2), 512, "classpath:prompts/physics-specification-system.txt",
                "classpath:prompts/physics-ocr-system.txt");
        var routingProperties = new JevProperties("", URI.create("https://example.test"), "test-jev",
                Duration.ofSeconds(2), 4, 0.5, 0.1, 2_000, 20_000);
        ChatCompletionClient client = spy(new ChatCompletionClient(RestClient.builder(), mapper, properties,
                mock(SchoolService.class), new DefaultResourceLoader()));
        List<ChatCompletionClient.Completion> completions = responseContents.stream()
                .map(content -> new ChatCompletionClient.Completion("test-model", content, mapper.createObjectNode()))
                .toList();
        doReturn(completions.getFirst(), completions.subList(1, completions.size()).toArray())
                .when(client).completeStructured(anyString(), anyList());
        SchemaVersion schema = approvedTestSchema();
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.requireCurrentApproved(contract.schemaId(), contract.schemaVersion())).thenReturn(schema);
        when(schemas.canonicalQuantityKey(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenAnswer(call -> call.getArgument(1));
        StructuredExtractionProvider provider = new StructuredExtractionProvider(client, mapper, units, schemas,
                properties, routingProperties, new DefaultResourceLoader(), new SimpleMeterRegistry(),
                mock(AssetSelectionService.class));
        return new ProviderFixture(provider, client);
    }

    private SchemaVersion approvedTestSchema() throws Exception {
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(contract.schemaId());
        schema.setVersion(contract.schemaVersion());
        schema.setTopic(contract.topic());
        schema.setName(contract.name());
        schema.setDefinition(mapper.readTree("""
                {"model":"test-motion-model","requiredQuantities":[
                  {"key":"initial_position","aliases":["x0"],"allowedUnits":["m"]},
                  {"key":"initial_velocity","aliases":["v0"],"allowedUnits":["m/s"]},
                  {"key":"acceleration","aliases":["a"],"allowedUnits":["m/s²"]}],
                  "entityContract":{"types":[{"type":"body","min":1,"max":1}]},
                  "execution":{"durationSeconds":8}}
                """));
        return schema;
    }

    private Specification openCapacitySpecification() throws Exception {
        JsonNode current = response(List.of(ambiguity(CompatibilityFieldPaths.CAPACITY,
                "jev.capacity.actor-count", "Bạn có đồng ý rút gọn mô phỏng không?")));
        Specification specification = new Specification();
        specification.setSchemaId(contract.schemaId());
        specification.setSchemaVersion(contract.schemaVersion());
        specification.setContractVersion("1.0");
        specification.setTopic(contract.topic());
        specification.setObjects(current.path("objects").deepCopy());
        specification.setQuantities(current.path("quantities").deepCopy());
        specification.setRelations(current.path("relations").deepCopy());
        specification.setEndCondition(current.path("endCondition").deepCopy());
        specification.setConfidence(BigDecimal.valueOf(0.9));
        specification.setAmbiguity(current.path("ambiguities").deepCopy());
        specification.setConfirmationState(ConfirmationState.UNRESOLVED);
        ProblemSubmission submission = new ProblemSubmission();
        submission.setEditableText(PROBLEM);
        specification.setSubmission(submission);
        AmbiguityCase ambiguity = new AmbiguityCase();
        ambiguity.setCode("jev.capacity.actor-count");
        ambiguity.setFieldPath(CompatibilityFieldPaths.CAPACITY);
        ambiguity.setQuestion("Bạn có đồng ý rút gọn mô phỏng không?");
        ambiguity.setOptions(mapper.createArrayNode());
        ambiguity.setStatus(AmbiguityStatus.OPEN);
        specification.addAmbiguityCase(ambiguity);
        return specification;
    }

    private record ProviderFixture(StructuredExtractionProvider provider, ChatCompletionClient client) { }

    private Map<String, Object> ambiguity(String path, String code, String question) {
        return Map.of("code", code, "fieldPath", path, "question", question, "options", List.of());
    }

    private void quantity(com.fasterxml.jackson.databind.node.ArrayNode target, String name, String symbol,
            double value, String unit) {
        var quantity = target.addObject();
        quantity.put("name", name);
        quantity.put("symbol", symbol);
        quantity.put("value", value);
        quantity.put("originalUnit", unit);
        quantity.putNull("confidence");
        quantity.putNull("sourceText");
    }

    private SchemaRoutingDecision decision(String reason, SchemaRoutingDecision.Status status) {
        return new SchemaRoutingDecision(status, reason, List.of(candidate), 0.95, 0.8);
    }

    private CandidateContractProjection contract() {
        return new CandidateContractProjection("test-approved-motion", "1.0", "KINEMATICS",
                "Approved motion candidate", "test-motion-model", List.of(
                        new CandidateContractProjection.QuantityProjection("initial_position", List.of("x0"),
                                List.of(), List.of("m"), Map.of(), null),
                        new CandidateContractProjection.QuantityProjection("initial_velocity", List.of("v0"),
                                List.of(), List.of("m/s"), Map.of(), null),
                        new CandidateContractProjection.QuantityProjection("acceleration", List.of("a"),
                                List.of(), List.of("m/s²"), Map.of(), null)),
                List.of(), List.of(), List.of("time_limit"),
                List.of(new CandidateContractProjection.EntityTypeProjection("body", 1, 1,
                        List.of(), List.of())), BigDecimal.valueOf(8));
    }
}
