package com.example.backend.physics.compatibility.legacy.reference.modern;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.model.modern.NuclearEnergyParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NuclearEnergyReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "nuclear_energy_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"nuclear_energy".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported nuclear-energy model: " + PhysicsValues.model(specification));
        NuclearEnergyParameters p = NuclearEnergyParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("releasedEnergy", p.releasedEnergy()));
    }
}
