package com.example.backend.physics.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.exception.CanonicalContractException;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.service.problem.SchemaCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CanonicalQuantityCompilerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CanonicalQuantityCompiler compiler = new CanonicalQuantityCompiler(new UnitNormalizer(mapper));
    private final CompiledSchema schema = schema();

    @Test
    void normalizesRawValueMaterializesCompiledDefaultAndAppliesBoundedAdjustment() throws Exception {
        JsonNode specification = mapper.readTree("""
                {"quantities":[
                  {"name":"mass","value":5000,"originalUnit":"g","normalizedValue":5,"normalizedUnit":"kg"}
                ]}
                """);

        var quantities = compiler.compile(schema, specification, Map.of("mass", 2.0));

        assertEquals(2.0, quantities.require("mass"));
        assertEquals("kg", quantities.unit("mass"));
        assertEquals(0.0, quantities.require("phase"));
        assertEquals("rad", quantities.unit("phase"));
        assertEquals(2.0, quantities.require("sample_count"));
    }

    @Test
    void rejectsAliasesBecauseCanonicalizationBelongsAtIngress() throws Exception {
        JsonNode specification = mapper.readTree("""
                {"quantities":[{"name":"m","value":5,"originalUnit":"kg"}]}
                """);

        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, specification, Map.of()));
    }

    @Test
    void rejectsAStoredNormalizationThatDisagreesWithRawInput() throws Exception {
        JsonNode specification = mapper.readTree("""
                {"quantities":[
                  {"name":"mass","value":5000,"originalUnit":"g","normalizedValue":7,"normalizedUnit":"kg"}
                ]}
                """);

        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, specification, Map.of()));
    }

    @Test
    void requiresRawValueAndOriginalUnitInsteadOfTrustingNormalizedFields() throws Exception {
        JsonNode normalizedOnly = mapper.readTree("""
                {"quantities":[
                  {"name":"mass","normalizedValue":5,"normalizedUnit":"kg"}
                ]}
                """);
        JsonNode stringValue = mapper.readTree("""
                {"quantities":[{"name":"mass","value":"5","originalUnit":"kg"}]}
                """);

        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, normalizedOnly, Map.of()));
        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, stringValue, Map.of()));
    }

    @Test
    void rejectsNonCanonicalNamesAndDuplicateKeys() throws Exception {
        JsonNode nonTextName = mapper.readTree("""
                {"quantities":[{"name":5,"value":5,"originalUnit":"kg"}]}
                """);
        JsonNode whitespaceName = mapper.readTree("""
                {"quantities":[{"name":" mass ","value":5,"originalUnit":"kg"}]}
                """);
        JsonNode duplicates = mapper.readTree("""
                {"quantities":[
                  {"name":"mass","value":5,"originalUnit":"kg"},
                  {"name":"mass","value":6,"originalUnit":"kg"}
                ]}
                """);

        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, nonTextName, Map.of()));
        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, whitespaceName, Map.of()));
        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, duplicates, Map.of()));
    }

    @Test
    void acceptsCanonicalUnitWhenSchemaAllowsAnEquivalentInputUnit() throws Exception {
        JsonNode specification = mapper.readTree("""
                {"quantities":[{"name":"mass","value":5000,"originalUnit":"g"}]}
                """);

        var quantities = compiler.compile(schema, specification, Map.of());

        assertEquals(5.0, quantities.require("mass"));
        assertEquals("kg", quantities.unit("mass"));
    }

    @Test
    void rejectsDomainAndAdjustmentViolations() throws Exception {
        JsonNode negative = mapper.readTree("""
                {"quantities":[{"name":"mass","value":-1,"originalUnit":"kg"}]}
                """);
        JsonNode aboveMaximum = mapper.readTree("""
                {"quantities":[{"name":"mass","value":11000,"originalUnit":"g"}]}
                """);
        JsonNode valid = mapper.readTree("""
                {"quantities":[{"name":"mass","value":5,"originalUnit":"kg"}]}
                """);

        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, negative, Map.of()));
        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, aboveMaximum, Map.of()));
        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, valid, Map.of("mass", 20.0)));
    }

    @Test
    void rejectsFractionalValuesForCompiledIntegerQuantities() throws Exception {
        JsonNode fractional = mapper.readTree("""
                {"quantities":[{"name":"sample_count","value":1.5,"originalUnit":"1"}]}
                """);

        assertThrows(CanonicalContractException.class, () -> compiler.compile(schema, fractional, Map.of()));
    }

    private CompiledSchema schema() {
        try {
            JsonNode definition = mapper.readTree("""
                    {
                      "version":"1.0","topic":"TEST","model":"mass_sample",
                      "requiredQuantities":[{"key":"mass","aliases":["m"],"allowedUnits":["kg","g"],"positive":true,"min":1,"max":10}],
                      "optionalQuantities":[
                        {"key":"phase","aliases":["phi"],"allowedUnits":["rad"],"defaultValue":0},
                        {"key":"sample_count","aliases":[],"allowedUnits":["1"],"defaultValue":2,"integer":true}
                      ],
                      "adjustableParameters":[{"key":"mass","min":1,"max":10,"step":0.1}],
                      "execution":{"durationSeconds":1,"stepSeconds":0.1},
                      "validation":{"tolerance":0.000001,"checkpointFractions":[0.5,1]},
                      "output":{"probeSeries":["position"]}
                    }
                    """);
            return new SchemaCompiler(mapper).compile(definition, "mass_schema");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
