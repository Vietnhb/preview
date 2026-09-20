package com.example.backend.physics.reference.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.dynamics.DampedForcedOscillationParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent closed-form checkpoint oracle for damped/forced oscillation. */
@Component
public class AdvancedOscillationReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "advanced_oscillation_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"damped_forced_oscillation".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported advanced oscillation model: "
                    + PhysicsValues.model(specification));
        }
        DampedForcedOscillationParameters p = DampedForcedOscillationParameters.from(specification, overrides);
        DampedForcedOscillationParameters.State state = p.stateAt(timeSeconds);
        double energy = 0.5 * p.mass() * state.velocity() * state.velocity()
                + 0.5 * p.springConstant() * state.displacement() * state.displacement();
        return new AnalyticalPoint(Map.of(
                "displacement", state.displacement(),
                "velocity", state.velocity(),
                "acceleration", state.acceleration(),
                "mechanicalEnergy", energy,
                "drivingForce", p.drivingAmplitude() * Math.cos(p.drivingFrequency() * timeSeconds)));
    }
}
