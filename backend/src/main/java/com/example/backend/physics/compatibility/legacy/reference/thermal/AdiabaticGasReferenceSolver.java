package com.example.backend.physics.compatibility.legacy.reference.thermal;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.compatibility.legacy.model.thermal.AdiabaticGasParameters;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AdiabaticGasReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "adiabatic_gas_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"adiabatic_gas".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported adiabatic-gas model: " + PhysicsValues.model(specification));
        AdiabaticGasParameters p = AdiabaticGasParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("initialPressure", p.initialPressure(), "finalPressure", p.finalPressure(), "finalTemperature", p.finalTemperature(), "work", p.workByGas()));
    }
}
