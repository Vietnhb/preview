package com.example.backend.physics.compatibility.legacy.reference.circuits;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.model.circuits.CapacitorParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CapacitorReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "capacitor_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"capacitor_basic".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported capacitor model: " + PhysicsValues.model(specification));
        CapacitorParameters p = CapacitorParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("charge", p.charge(), "energy", p.energy(), "voltage", p.voltage()));
    }
}
