package com.example.backend.physics;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.validation.OutputContractValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
