package com.example.backend.physics.compatibility.legacy.reference.waves;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.waves.WaveReflectionParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent analytical probe evaluator for the reflection image solution. */
@Component
public class WaveReflectionReferenceSolver implements ReferenceSolver {
    @Override
    public String solverId() {
        return "wave_reflection_reference";
    }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"wave_reflection".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException(
                    "Unsupported reflection reference model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds))
            throw new IllegalArgumentException("timeSeconds must be finite");
        WaveReflectionParameters parameters = WaveReflectionParameters.from(specification, overrides);
        double time = Math.max(0, timeSeconds);
        State incident = gaussian(parameters, (parameters.probePosition() - parameters.initialPosition()
                - parameters.waveSpeed() * time) / parameters.width());
        State reflected = gaussian(parameters, (2 * parameters.boundaryPosition() - parameters.probePosition()
                - parameters.initialPosition() - parameters.waveSpeed() * time) / parameters.width());
        double coefficient = parameters.reflectionCoefficient();
        return new AnalyticalPoint(Map.of(
                "displacement", incident.displacement() + coefficient * reflected.displacement(),
                "particleVelocity", incident.velocity() + coefficient * reflected.velocity(),
                "particleAcceleration", incident.acceleration() + coefficient * reflected.acceleration()));
    }

    private static State gaussian(WaveReflectionParameters parameters, double q) {
        double envelope = Math.exp(-q * q);
        double cOverWidth = parameters.waveSpeed() / parameters.width();
        return new State(parameters.amplitude() * envelope,
                parameters.amplitude() * envelope * 2 * q * cOverWidth,
                parameters.amplitude() * envelope * (4 * q * q - 2) * cOverWidth * cOverWidth);
    }

    private record State(double displacement, double velocity, double acceleration) {
    }
}
