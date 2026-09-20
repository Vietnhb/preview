package com.example.backend.schema.routing.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.schema.routing.index.IndexedSchemaCandidate;
import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaContractRerankerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaContractReranker reranker = new SchemaContractReranker(
            new UnitNormalizer(mapper), properties());

    @Test
    void recognizesCompoundUnitsFromTheSharedUnitCatalog() throws Exception {
        IndexedSchemaCandidate candidate = candidate("speed_schema", "Speed", """
                {"model":"speed_model","requiredQuantities":[
                  {"key":"initial_velocity","aliases":["speed"],"allowedUnits":["m/s"],"positive":true}
                ],"optionalQuantities":[]}
                """);

        var result = reranker.verify("The speed is 12 m/s.", candidate);

        assertEquals(1.0, result.evidence().unitCompatibility());
        assertEquals(0.0, result.evidence().contractContradiction());
    }

    @Test
    void penalizesExplicitValuesThatContradictGenericQuantityConstraints() throws Exception {
        IndexedSchemaCandidate candidate = candidate("mass_schema", "Mass", """
                {"model":"mass_model","requiredQuantities":[
                  {"key":"mass","aliases":["mass"],"allowedUnits":["kg"],"positive":true,"min":0.1}
                ],"optionalQuantities":[]}
                """);

        var valid = reranker.verify("The mass is 2 kg.", candidate);
        var invalid = reranker.verify("The mass is -2 kg.", candidate);

        assertEquals(0.0, valid.evidence().contractContradiction());
        assertEquals(1.0, invalid.evidence().contractContradiction());
        assertTrue(invalid.evidence().evidenceCodes().contains("CONTRACT_CONTRADICTION"));
        assertTrue(invalid.confidence() < valid.confidence());
    }

    @Test
    void treatsRecognizedButDisallowedQuantityUnitsAsNegativeContractEvidence() throws Exception {
        IndexedSchemaCandidate candidate = candidate("mass_schema", "Mass", """
                {"model":"mass_model","requiredQuantities":[
                  {"key":"mass","aliases":["mass"],"allowedUnits":["kg"],"positive":true}
                ],"optionalQuantities":[]}
                """);

        var result = reranker.verify("The mass is 2 m.", candidate);

        assertEquals(1.0, result.evidence().contractContradiction());
    }

    @Test
    void appliesDeclaredSameUnitAsRelationToIndividuallyAllowedUnits() throws Exception {
        IndexedSchemaCandidate candidate = candidate("uncertainty_schema", "Measurement uncertainty", """
                {"model":"uncertainty_model","requiredQuantities":[
                  {"key":"measured_value","aliases":["measured value"],"allowedUnits":["m","cm","s"]},
                  {"key":"absolute_uncertainty","aliases":["uncertainty"],"allowedUnits":["m","cm","s"],
                   "sameUnitAs":"measured_value"}
                ],"optionalQuantities":[]}
                """);

        var incompatible = reranker.verify("measured value is 4 m; uncertainty is 0.2 s", candidate);
        var compatibleAfterNormalization = reranker.verify("measured value is 4 m; uncertainty is 20 cm", candidate);

        assertEquals(1.0, incompatible.evidence().contractContradiction());
        assertEquals(0.0, compatibleAfterNormalization.evidence().contractContradiction());
    }

    @Test
    void explicitQuantityAssignmentCanAssociateBeyondTheProximityWindow() throws Exception {
        SchemaContractReranker narrowWindowReranker = new SchemaContractReranker(
                new UnitNormalizer(mapper), properties(1));
        IndexedSchemaCandidate candidate = candidate("mass_schema", "Mass", """
                {"model":"mass_model","requiredQuantities":[
                  {"key":"mass","aliases":["mass"],"allowedUnits":["kg"],"positive":true}
                ],"optionalQuantities":[]}
                """);

        var result = narrowWindowReranker.verify("mass equals -2 kg", candidate);

        assertEquals(1.0, result.evidence().contractContradiction());
    }

    private IndexedSchemaCandidate candidate(String id, String name, String definition) throws Exception {
        var json = mapper.readTree(definition);
        var contract = CandidateContractProjection.from(id, "1.0", "DYNAMICS", name, json);
        var document = new SchemaSearchDocument(id, "1.0", "DYNAMICS", name, contract.modelId(),
                name + " " + definition, "checksum-" + id);
        return new IndexedSchemaCandidate(document, contract);
    }

    private static SchemaRoutingProperties properties() {
        return properties(64);
    }

    private static SchemaRoutingProperties properties(int associationWindow) {
        return new SchemaRoutingProperties(true, 20, 20, 5, 60,
                0.25, 0.08, 20_000, 30_000, associationWindow, 1.2, 0.75, 0.15,
                0.55, 0.25, 0.20, 0.75,
                new SchemaRoutingProperties.Embedding("test", "fixture-v1", 2, Duration.ofSeconds(2)));
    }
}
