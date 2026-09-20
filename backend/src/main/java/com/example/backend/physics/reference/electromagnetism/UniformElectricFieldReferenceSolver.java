package com.example.backend.physics.reference.electromagnetism;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.electromagnetism.UniformElectricFieldParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UniformElectricFieldReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "uniform_electric_field_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"uniform_electric_field".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported uniform-field model: " + PhysicsValues.model(specification));
        }
        UniformElectricFieldParameters p = UniformElectricFieldParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("fieldStrength", p.fieldStrength(), "electricForce", p.electricForce(),
                "acceleration", p.acceleration(), "transverseDisplacement", p.transverseDisplacement(),
                "longitudinalDisplacement", p.longitudinalDisplacement()));
    }
}
