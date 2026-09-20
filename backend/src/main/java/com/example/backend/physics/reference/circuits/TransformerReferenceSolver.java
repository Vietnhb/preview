package com.example.backend.physics.reference.circuits;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.circuits.TransformerParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class TransformerReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "transformer_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"ideal_transformer".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported transformer model: " + PhysicsValues.model(specification));
        TransformerParameters p = TransformerParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("turnsRatio", p.turnsRatio(), "secondaryVoltage", p.secondaryVoltage(), "primaryCurrent", p.primaryCurrent()));
    }
}
