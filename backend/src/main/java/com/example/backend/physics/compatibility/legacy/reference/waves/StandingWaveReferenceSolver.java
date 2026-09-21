package com.example.backend.physics.compatibility.legacy.reference.waves;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.waves.StandingWaveParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StandingWaveReferenceSolver implements ReferenceSolver {
    @Override
    public String solverId() {
        return "standing_wave_reference";
    }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"standing_wave".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException(
                    "Unsupported standing-wave model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds))
            throw new IllegalArgumentException("timeSeconds must be finite");
        StandingWaveParameters parameters = StandingWaveParameters.from(specification, overrides);
        double x = parameters.probePosition() - parameters.domainStart();
        double shape = Math.sin(parameters.waveNumber() * x);
        double temporal = parameters.angularFrequency() * Math.max(0, timeSeconds) + parameters.phase();
        double displacement = parameters.amplitude() * shape * Math.cos(temporal);
        double velocity = -parameters.amplitude() * shape * parameters.angularFrequency() * Math.sin(temporal);
        double acceleration = -parameters.angularFrequency() * parameters.angularFrequency() * displacement;
        return new AnalyticalPoint(Map.of("displacement", displacement, "particleVelocity", velocity,
                "particleAcceleration", acceleration));
    }
}
