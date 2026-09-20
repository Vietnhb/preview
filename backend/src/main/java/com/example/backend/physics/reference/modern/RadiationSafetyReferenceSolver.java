package com.example.backend.physics.reference.modern;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.modern.RadiationSafetyParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class RadiationSafetyReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "radiation_safety_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"radiation_safety".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported radiation-safety model: " + PhysicsValues.model(specification));
        return new AnalyticalPoint(Map.of("doseRate", RadiationSafetyParameters.from(specification, overrides).doseRate()));
    }
}
