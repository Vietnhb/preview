package com.example.backend.physics.reference.thermal;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.thermal.FirstLawParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class FirstLawReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "first_law_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"first_law_thermodynamics".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported first-law model: " + PhysicsValues.model(specification));
        FirstLawParameters p = FirstLawParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("internalEnergy", p.finalInternalEnergy(), "deltaInternalEnergy", p.deltaInternalEnergy(),
                "heat", p.heatAdded(), "work", p.workDone()));
    }
}
