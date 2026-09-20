package com.example.backend.physics.reference.electromagnetism;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.electromagnetism.InductionParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class InductionReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "induction_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"electromagnetic_induction".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported induction model: " + PhysicsValues.model(specification));
        InductionParameters p = InductionParameters.from(specification, overrides);
        double t = Math.max(0, timeSeconds), b = p.magneticField() + p.fieldRate() * t;
        return new AnalyticalPoint(Map.of("magneticField", b, "magneticFlux", b * p.coilArea() * Math.cos(p.coilAngle()), "inducedEmf", p.inducedEmf()));
    }
}
