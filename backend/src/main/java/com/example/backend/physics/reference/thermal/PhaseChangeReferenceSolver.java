package com.example.backend.physics.reference.thermal;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.thermal.PhaseChangeParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent checkpoint oracle for the phase-change heating curve. */
@Component
public class PhaseChangeReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "phase_change_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"phase_change".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported phase-change model: " + PhysicsValues.model(specification));
        }
        PhaseChangeParameters p = PhaseChangeParameters.from(specification, overrides);
        PhaseChangeParameters.State state = p.stateAtEnergy(p.heatingPower() * timeSeconds);
        return new AnalyticalPoint(Map.of(
                "temperature", state.temperature(),
                "heatAdded", p.heatingPower() * timeSeconds,
                "liquidFraction", state.liquidFraction(),
                "vaporFraction", state.vaporFraction()));
    }
}
