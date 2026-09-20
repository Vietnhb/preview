package com.example.backend.physics.reference.optics;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.optics.LightInterferenceParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class LightInterferenceReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "light_interference_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"light_interference".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported light-interference model: " + PhysicsValues.model(specification));
        LightInterferenceParameters p = LightInterferenceParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("phaseDifference", p.phaseDifference(), "intensity", p.intensity()));
    }
}
