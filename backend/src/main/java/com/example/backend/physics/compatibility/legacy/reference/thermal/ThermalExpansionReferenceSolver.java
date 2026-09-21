package com.example.backend.physics.compatibility.legacy.reference.thermal;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.thermal.ThermalExpansionParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ThermalExpansionReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "thermal_expansion_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"thermal_expansion".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported thermal-expansion model: " + PhysicsValues.model(specification));
        ThermalExpansionParameters p = ThermalExpansionParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("deltaTemperature", p.deltaTemperature(), "extension", p.extension(), "finalLength", p.finalLength()));
    }
}
