package com.example.backend.physics.compatibility.legacy.reference.waves;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.waves.WavePulseParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WavePulseReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "wave_pulse_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"wave_pulse".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported pulse model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("timeSeconds must be finite");
        WavePulseParameters parameters = WavePulseParameters.from(specification, overrides);
        double q = (parameters.probePosition() - parameters.initialPosition()
                - parameters.waveSpeed() * Math.max(0, timeSeconds)) / parameters.width();
        double envelope = Math.exp(-q * q);
        double cOverWidth = parameters.waveSpeed() / parameters.width();
        double displacement = parameters.amplitude() * envelope;
        double velocity = parameters.amplitude() * envelope * 2 * q * cOverWidth;
        double acceleration = parameters.amplitude() * envelope * (4 * q * q - 2) * cOverWidth * cOverWidth;
        return new AnalyticalPoint(Map.of("displacement", displacement, "particleVelocity", velocity,
                "particleAcceleration", acceleration));
    }
}
