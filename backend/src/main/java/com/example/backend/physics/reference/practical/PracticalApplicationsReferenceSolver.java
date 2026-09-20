package com.example.backend.physics.reference.practical;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.modern.EnergyEnvironmentParameters;
import com.example.backend.physics.reference.TopicReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Practical/data application reference oracle. */
@Component
public class PracticalApplicationsReferenceSolver implements TopicReferenceSolver {
    @Override public String solverId() { return "practical_applications_reference"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("energy_environment"); }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double time) {
        if (!"energy_environment".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported practical application model: " + PhysicsValues.model(specification));
        }
        EnergyEnvironmentParameters p = EnergyEnvironmentParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("renewableEnergy", p.renewableEnergy(), "fossilEnergy", p.fossilEnergy(),
                "emissions", p.emissions(), "usefulEnergy", p.usefulEnergy(),
                "avoidedEmissionsVsFossil", p.avoidedEmissionsVsFossil()));
    }
}
