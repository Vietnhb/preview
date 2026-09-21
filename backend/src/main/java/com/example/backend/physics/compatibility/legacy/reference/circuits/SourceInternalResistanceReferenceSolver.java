package com.example.backend.physics.compatibility.legacy.reference.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.circuits.SourceInternalResistanceParameters;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SourceInternalResistanceReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "source_internal_resistance_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"source_internal_resistance".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported source model: " + PhysicsValues.model(specification));
        }
        SourceInternalResistanceParameters p = SourceInternalResistanceParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("current", p.current(), "terminalVoltage", p.terminalVoltage(),
                "loadPower", p.loadPower(), "internalPowerLoss", p.internalPowerLoss(), "efficiency", p.efficiency()));
    }
}
