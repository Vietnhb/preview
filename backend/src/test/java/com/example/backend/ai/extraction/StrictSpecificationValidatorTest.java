package com.example.backend.ai.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.schema.routing.model.SchemaSelectionScore;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictSpecificationValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsCanonicalNumericSpecification() throws Exception {
        assertDoesNotThrow(() -> StrictSpecificationValidator.validate(mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"3.1","topic":"DYNAMICS","schemaId":"hooke_law",
                 "objects":[],"quantities":[{"name":"spring_constant","symbol":"k","value":2.0,
                 "originalUnit":"N/m","confidence":1.0}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1.0,"ambiguities":[]}
                """)));
    }

    @Test
    void rejectsNumericStringsAndUnknownFieldsBeforeBinding() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"3.1","schemaId":"hooke_law","objects":[],"quantities":[],
                 "relations":[],"endCondition":{"type":"time_limit","duration":1},
                 "confidence":1.0,"ambiguities":[]}
                """);
        root.withArray("quantities").addObject().put("name", "spring_constant")
                .put("value", "2").put("originalUnit", "N/m");
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
        root.withArray("quantities").removeAll();
        root.put("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
    }

    @Test
    void rejectsDuplicateQuantityAndAmbiguityIdentity() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"3.1","schemaId":"hooke_law","objects":[],
                 "quantities":[{"name":"k","value":2,"originalUnit":"N/m"},
                 {"name":"k","value":3,"originalUnit":"N/m"}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1.0,
                 "ambiguities":[]}
                """);
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
        root.withArray("quantities").removeAll();
        root.withArray("ambiguities").addObject().put("code", "missing.k")
                .put("fieldPath", "quantities.k").put("question", "k?").putArray("options");
        root.withArray("ambiguities").addObject().put("code", "other")
                .put("fieldPath", "quantities.k").put("question", "k?").putArray("options");
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
    }

    @Test
    void rejectsAiNormalizedQuantityFieldsAndKeepsDefinitionVersionSeparate() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"4.2","schemaId":"hooke_law","objects":[],
                 "quantities":[{"name":"spring_constant","value":2,"originalUnit":"N/m",
                 "normalizedValue":2,"normalizedUnit":"N/m"}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1,"ambiguities":[]}
                """);
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
    }

    @Test
    void enforcesPinnedCandidateVersionAndCandidateQuantityVocabularyBeforeBinding() throws Exception {
        CandidateContractProjection contract = CandidateContractProjection.from("hooke_law", "4.2", "DYNAMICS",
                "Hooke law", mapper.readTree("""
                        {"model":"hooke_law","requiredQuantities":[{"key":"spring_constant",
                         "aliases":["k"],"allowedUnits":["N/m"]}],"optionalQuantities":[]}
                        """));
        SchemaCandidate candidate = new SchemaCandidate(contract, new SchemaSelectionScore(1, 1),
                new VerificationEvidence(1, 1, 1, java.util.List.of("fixture")), 1);
        SchemaRoutingDecision decision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "fixture", java.util.List.of(candidate), 1, 1);
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"4.2","topic":"DYNAMICS","schemaId":"hooke_law",
                 "objects":[],"quantities":[{"name":"k","value":2,"originalUnit":"N/m"}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1,"ambiguities":[]}
                """);

        assertEquals(candidate, StrictSpecificationValidator.validateCandidateMembership(root, decision));
        root.put("schemaVersion", "4.1");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));
        root.put("schemaVersion", "4.2");
        ((ObjectNode) root.withArray("quantities").get(0)).put("name", "mass");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));
        root.withArray("quantities").removeAll();
        root.withArray("ambiguities").addObject().put("code", "schema.selection")
                .put("fieldPath", "schemaId").put("question", "Select model?")
                .putArray("options").add("unrouted_schema@9.9");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));
    }

    @Test
    void rejectsUnknownAndCandidateDisallowedUnitsBeforeJacksonBinding() throws Exception {
        SchemaRoutingDecision decision = decisionFor("hooke_law", "4.2", "DYNAMICS", "Hooke law", mapper.readTree("""
                {"model":"hooke_law","requiredQuantities":[{"key":"spring_constant",
                 "aliases":["k"],"allowedUnits":["N/m"]}],"optionalQuantities":[]}
                """));
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"4.2","topic":"DYNAMICS","schemaId":"hooke_law",
                 "objects":[],"quantities":[{"name":"k","value":2,"originalUnit":"s"}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1,"ambiguities":[]}
                """);
        UnitNormalizer units = new UnitNormalizer(mapper);

        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision, units));
        ((ObjectNode) root.withArray("quantities").get(0)).put("originalUnit", "unknown-unit");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision, units));
        ((ObjectNode) root.withArray("quantities").get(0)).put("originalUnit", "N / m");
        assertDoesNotThrow(() -> StrictSpecificationValidator.validateCandidateMembership(root, decision, units));
    }

    @Test
    void selectedDecisionCannotBeOverriddenButAmbiguousDecisionMayChooseOnlyPinnedCandidate() throws Exception {
        var definition = mapper.readTree("""
                {"model":"fixture","requiredQuantities":[{"key":"mass","aliases":["m"],
                 "allowedUnits":["kg"]}],"optionalQuantities":[]}
                """);
        SchemaCandidate selected = decisionFor("model_a", "2.0", "DYNAMICS", "Model A", definition)
                .candidates().getFirst();
        SchemaCandidate other = decisionFor("model_b", "1.1", "DYNAMICS", "Model B", definition)
                .candidates().getFirst();
        SchemaRoutingDecision selectedDecision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "BACKEND_SELECTED", java.util.List.of(selected, other), selected.confidence(), 0.5);
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.1","topic":"DYNAMICS","schemaId":"model_b",
                 "objects":[],"quantities":[{"name":"mass","value":1,"originalUnit":"kg"}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1,"ambiguities":[]}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, selectedDecision));

        SchemaRoutingDecision ambiguousDecision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.AMBIGUOUS,
                "LOW_MARGIN", java.util.List.of(selected, other), selected.confidence(), 0);
        assertEquals(other, StrictSpecificationValidator.validateCandidateMembership(root, ambiguousDecision));
        root.put("schemaId", "model_outside");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, ambiguousDecision));
    }

    @Test
    void rejectsDifferentAliasesThatResolveToTheSameCanonicalQuantityBeforeBinding() throws Exception {
        SchemaRoutingDecision decision = decisionFor("hooke_law", "4.2", "DYNAMICS", "Hooke law", mapper.readTree("""
                {"model":"hooke_law","requiredQuantities":[{"key":"spring_constant",
                 "aliases":["k"],"allowedUnits":["N/m"]}],"optionalQuantities":[]}
                """));
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"4.2","topic":"DYNAMICS","schemaId":"hooke_law",
                 "objects":[],"quantities":[
                   {"name":"spring_constant","value":2,"originalUnit":"N/m"},
                   {"name":"k","value":3,"originalUnit":"N/m"}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1,"ambiguities":[]}
                """);

        StrictSpecificationValidator.validate(root);
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));
    }

    @Test
    void enforcesDeclaredRelationTypesAndEndConditionCapabilities() throws Exception {
        SchemaRoutingDecision decision = decisionFor("mechanics_events", "2.0", "DYNAMICS", "Mechanics events",
                mapper.readTree("""
                        {"model":"mechanics_events","requiredQuantities":[{"key":"mass",
                         "aliases":[],"allowedUnits":["kg"]}],"optionalQuantities":[],
                         "relationTypes":["contact"],"endConditionCapabilities":["time_limit"]}
                        """));
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"2.0","topic":"DYNAMICS","schemaId":"mechanics_events",
                 "objects":[],"quantities":[{"name":"mass","value":1,"originalUnit":"kg"}],
                 "relations":[{"type":"collision","subject":"a"}],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1,"ambiguities":[]}
                """);

        StrictSpecificationValidator.validate(root);
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));

        ((ObjectNode) root.withArray("relations").get(0)).put("type", "contact");
        root.with("endCondition").put("type", "threshold");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));

        root.with("endCondition").put("type", "time_limit");
        assertEquals(decision.candidates().getFirst(),
                StrictSpecificationValidator.validateCandidateMembership(root, decision));
    }

    @Test
    void executionDurationProvidesOnlyTheGenericTimeLimitCapabilityWhenNoneIsDeclared() throws Exception {
        SchemaRoutingDecision decision = decisionFor("timed_model", "1.0", "DYNAMICS", "Timed model",
                mapper.readTree("""
                        {"model":"timed_model","requiredQuantities":[{"key":"mass",
                         "aliases":[],"allowedUnits":["kg"]}],"optionalQuantities":[],
                         "execution":{"durationSeconds":2}}
                        """));
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.0","topic":"DYNAMICS","schemaId":"timed_model",
                 "objects":[],"quantities":[{"name":"mass","value":1,"originalUnit":"kg"}],"relations":[],
                 "endCondition":{"type":"threshold","quantity":"mass","value":2},"confidence":1,"ambiguities":[]}
                """);

        assertDoesNotThrow(() -> StrictSpecificationValidator.validate(root));
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));

        root.with("endCondition").put("type", "time_limit").put("duration", 2);
        assertDoesNotThrow(() -> StrictSpecificationValidator.validateCandidateMembership(root, decision));
    }

    @Test
    void everyMissingRequiredQuantityMustHaveOneQuestionAndOptionalQuantitiesMustNotBlock() throws Exception {
        SchemaRoutingDecision decision = decisionFor("motion", "1.0", "KINEMATICS", "Motion",
                mapper.readTree("""
                        {"model":"motion","requiredQuantities":[{"key":"velocity",
                         "aliases":["v"],"allowedUnits":["m/s"]}],
                         "optionalQuantities":[{"key":"color","aliases":[],"allowedUnits":["1"]}]}
                        """));
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.0","topic":"KINEMATICS","schemaId":"motion",
                 "objects":[],"quantities":[],"relations":[],"endCondition":{"type":"time_limit","duration":1},
                 "confidence":0.5,"ambiguities":[]}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));

        root.withArray("ambiguities").addObject().put("code", "missing.velocity")
                .put("fieldPath", "quantities.velocity").put("question", "Vận tốc là bao nhiêu?")
                .putArray("options");
        assertDoesNotThrow(() -> StrictSpecificationValidator.validateCandidateMembership(root, decision));

        ((ObjectNode) root.withArray("ambiguities").get(0)).put("fieldPath", "quantities.color");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision));
    }

    @Test
    void doesNotAskForAQuantityThatIsExplicitlyPresentInOriginalText() throws Exception {
        var definition = mapper.readTree("""
                {"model":"motion","requiredQuantities":[{"key":"velocity",
                 "aliases":["v"],"allowedUnits":["m/s"]}],"optionalQuantities":[]}
                """);
        var contract = CandidateContractProjection.from("motion", "1.0", "KINEMATICS", "Motion", definition);
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.0","topic":"KINEMATICS","schemaId":"motion",
                 "objects":[],"quantities":[],"relations":[],"endCondition":{"type":"time_limit","duration":1},
                 "confidence":0.5,"ambiguities":[{"code":"missing.velocity","fieldPath":"quantities.velocity",
                 "question":"Vận tốc là bao nhiêu?","options":[]}]}
                """);

        assertThrows(IllegalArgumentException.class, () ->
                StrictSpecificationValidator.rejectAmbiguitiesCoveredBySource(root,
                        "Một vật chuyển động với vận tốc đầu 10 m/s trong 8 giây.", contract));
        assertDoesNotThrow(() -> StrictSpecificationValidator.rejectAmbiguitiesCoveredBySource(root,
                "Hãy mô phỏng chuyển động của vật.", contract));
    }

    @Test
    void doesNotAskForAQuantityAlreadyPresentInConfirmedSpecificationEvidence() throws Exception {
        var definition = mapper.readTree("""
                {"model":"motion","requiredQuantities":[{"key":"initial_position",
                 "aliases":["x0"],"allowedUnits":["m"]}],"optionalQuantities":[]}
                """);
        var contract = CandidateContractProjection.from("motion", "1.0", "KINEMATICS", "Motion", definition);
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.0","topic":"KINEMATICS","schemaId":"motion",
                 "objects":[],"quantities":[],"relations":[],"endCondition":{"type":"time_limit","duration":1},
                 "confidence":0.5,"ambiguities":[{"code":"missing.initial_position","fieldPath":"quantities.initial_position",
                 "question":"Vị trí ban đầu là bao nhiêu?","options":[]}]}
                """);

        assertThrows(IllegalArgumentException.class, () ->
                StrictSpecificationValidator.rejectAmbiguitiesCoveredBySource(root,
                        "Confirmed specification: {\"quantities\":[{\"name\":\"initial_position\",\"value\":0,\"originalUnit\":\"m\"}]}\n"
                                + "Teacher answers: {\"missing.initial_position\":\"0 m\"}", contract));
    }

    @Test
    void validatesDynamicEntityQuantitiesAgainstTheEntityContract() throws Exception {
        var definition = mapper.readTree("""
                {"model":"multi_body_motion","requiredQuantities":[],"optionalQuantities":[],
                 "entityContract":{"types":[{"type":"moving_body","min":2,"max":4,
                   "requiredQuantities":[{"key":"mass","aliases":["m"],"allowedUnits":["kg"]},
                     {"key":"velocity","aliases":["v"],"allowedUnits":["m/s"]}],"optionalQuantities":[]}]}}
                """);
        SchemaRoutingDecision decision = decisionFor("multi_body_motion", "1.0", "DYNAMICS", "Multi-body motion", definition);
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.0","topic":"DYNAMICS","schemaId":"multi_body_motion",
                 "objects":[
                   {"id":"body-a","label":"A","type":"moving_body","quantities":[
                     {"name":"m","value":2,"originalUnit":"kg"},{"name":"v","value":3,"originalUnit":"m/s"}]},
                   {"id":"body-b","label":"B","type":"moving_body","quantities":[
                     {"name":"mass","value":3,"originalUnit":"kg"},{"name":"velocity","value":-1,"originalUnit":"m/s"}]}],
                 "quantities":[],"relations":[],"endCondition":{"type":"time_limit","duration":1},
                 "confidence":1,"ambiguities":[]}
                """);
        StrictSpecificationValidator.validate(root);
        assertEquals(decision.candidates().getFirst(),
                StrictSpecificationValidator.validateCandidateMembership(root, decision, new UnitNormalizer(mapper)));

        ((ObjectNode) root.withArray("objects").get(1).withArray("quantities").get(1)).put("originalUnit", "s");
        assertThrows(IllegalArgumentException.class,
                () -> StrictSpecificationValidator.validateCandidateMembership(root, decision, new UnitNormalizer(mapper)));
    }

    @Test
    void acceptsNullableEventEntitiesDeclaredByTheResponseSchema() throws Exception {
        var root = mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.0","topic":"DYNAMICS","schemaId":"test",
                 "objects":[],"quantities":[],"relations":[],"confidence":1,"ambiguities":[],
                 "endCondition":{"type":"event","event":{"type":"threshold","entities":null}}}
                """);
        StrictSpecificationValidator.validate(root);
    }

    private SchemaRoutingDecision decisionFor(String schemaId, String schemaVersion, String topic, String name,
            com.fasterxml.jackson.databind.JsonNode definition) {
        CandidateContractProjection contract = CandidateContractProjection.from(
                schemaId, schemaVersion, topic, name, definition);
        SchemaCandidate candidate = new SchemaCandidate(contract, new SchemaSelectionScore(1, 1),
                new VerificationEvidence(1, 1, 1, java.util.List.of("fixture")), 1);
        return new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED, "fixture",
                java.util.List.of(candidate), 1, 1);
    }
}
