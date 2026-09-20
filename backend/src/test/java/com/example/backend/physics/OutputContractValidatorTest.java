package com.example.backend.physics;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.exception.OutputContractException;
import com.example.backend.physics.validation.OutputContractValidator;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.ScalarOutput;
import com.example.backend.physics.compatibility.LegacySolverOutputAdapter;
import com.example.backend.service.problem.SchemaCompiler;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputContractValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validatesDeclaredSeriesAndStrictTimeline() {
        SolverOutput output = new SolverOutput(List.of(0.0, 1.0), Map.of(), Map.of(), Map.of(),
                Map.of("position", List.of(0.0, 1.0)));
        assertDoesNotThrow(() -> OutputContractValidator.validate("test", mapper.readTree(
                "{\"output\":{\"probeSeries\":[\"position\"]}}"), output));
    }

    @Test
    void rejectsMissingSeriesAndNonMonotonicTimeline() {
        SolverOutput missing = new SolverOutput(List.of(0.0, 1.0), Map.of(), Map.of(), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> OutputContractValidator.validate("missing", mapper.readTree(
                "{\"output\":{\"probeSeries\":[\"position\"]}}"), missing));
        SolverOutput nonMonotonic = new SolverOutput(List.of(0.0, 0.0), Map.of(), Map.of(), Map.of(),
                Map.of("position", List.of(0.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> OutputContractValidator.validate("time", mapper.createObjectNode(), nonMonotonic));
    }

    @Test
    void compilesAndValidatesDataDrivenScalarOutputDeclarations() throws Exception {
        var definition = mapper.readTree("""
                {
                  "version":"1.1", "topic":"MODERN_PHYSICS", "model":"radiation_safety",
                  "requiredQuantities":[], "optionalQuantities":[], "adjustableParameters":[],
                  "execution":{"durationSeconds":1,"stepSeconds":0.1},
                  "validation":{"tolerance":0.000001,"checkpointFractions":[0.25,0.5,0.75,1]},
                  "output":{"definitions":[
                    {"key":"doseRate","kind":"scalar","unit":"Gy/s","required":true}
                  ]}
                }
                """);
        var compiled = new SchemaCompiler(mapper).compile(definition, "radiation_safety");
        SolverOutput scalar = new SolverOutput(List.of(0.0, 0.5, 1.0), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of("doseRate", 1.0));

        assertEquals(Set.of("doseRate"), compiled.outputKeys());
        assertEquals(PhysicsOutput.OutputKind.SCALAR,
                compiled.outputDefinitions().get("doseRate").kind());
        assertDoesNotThrow(() -> OutputContractValidator.validate(compiled, definition, scalar));

        SolverOutput repeatedSeries = new SolverOutput(List.of(0.0, 0.5, 1.0), Map.of(), Map.of(), Map.of(),
                Map.of("doseRate", List.of(1.0, 1.0, 1.0)));
        assertThrows(OutputContractException.class,
                () -> OutputContractValidator.validate(compiled, definition, repeatedSeries));
    }

    @Test
    void scalarAdapterKeepsOneValueIndependentOfTimelineAndRejectsDuplicateKey() {
        SolverOutput scalar = new SolverOutput(List.of(0.0, 0.5, 1.0), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of("doseRate", 1.0));
        var frame = LegacySolverOutputAdapter.adapt(scalar, Map.of("doseRate", "Gy/s"));

        assertEquals(1, frame.outputs().size());
        assertEquals(1.0, ((ScalarOutput) frame.outputs().getFirst()).value(), 0.0);
        assertEquals(PhysicsOutput.OutputKind.SCALAR, frame.outputs().getFirst().kind());

        SolverOutput duplicated = new SolverOutput(List.of(0.0, 0.5, 1.0), Map.of(), Map.of(), Map.of(),
                Map.of("doseRate", List.of(1.0, 1.0, 1.0)), Map.of(), Map.of("doseRate", 1.0));
        assertThrows(IllegalArgumentException.class, () -> OutputContractValidator.validate("duplicate",
                mapper.createObjectNode(), duplicated));
    }

    @Test
    void solverOutputCopiesAndValidatesStandaloneScalarValues() {
        Map<String, Double> source = new LinkedHashMap<>();
        source.put("doseRate", 2.0);
        SolverOutput output = new SolverOutput(List.of(0.0), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), source);
        source.put("doseRate", 3.0);

        assertEquals(Map.of("doseRate", 2.0), output.scalarOutputs());
        assertThrows(UnsupportedOperationException.class, () -> output.scalarOutputs().put("other", 1.0));
        assertThrows(IllegalArgumentException.class, () -> new SolverOutput(List.of(0.0), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of("doseRate", Double.NaN)));
    }

    @Test
    void scalarDefinitionsCannotDriveDynamicEndConditions() throws Exception {
        var definition = mapper.readTree("""
                {
                  "execution":{"durationSeconds":1},
                  "output":{"definitions":[
                    {"key":"doseRate","kind":"scalar","unit":"Gy/s","required":true}
                  ]}
                }
                """);
        var specification = mapper.readTree("""
                {"endCondition":{"type":"threshold","quantity":"values.doseRate","operator":">","value":1}}
                """);
        var service = new SchemaDefinitionService(null, null, null);

        assertTrue(service.validateSpecification(specification, definition).stream()
                .anyMatch(error -> error.contains("cannot reference a scalar output")));
    }
}
