package com.example.backend.physics.compatibility.legacy.reference.optics;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.optics.RefractionParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class RefractionReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "refraction_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"snell_refraction".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported refraction model: " + PhysicsValues.model(specification));
        RefractionParameters p = RefractionParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("refractedAngle", p.refractedAngle(), "totalInternalReflection", p.totalInternalReflection() ? 1.0 : 0.0));
    }
}
