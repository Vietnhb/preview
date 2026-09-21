package com.example.backend.physics.compatibility.legacy.reference.waves;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.waves.WaveSuperpositionParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent analytical probe evaluator for two-wave superposition. */
@Component
public class WaveSuperpositionReferenceSolver implements ReferenceSolver {
    @Override
    public String solverId() {
        return "wave_superposition_reference";
    }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"wave_superposition".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException(
                    "Unsupported superposition reference model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds))
            throw new IllegalArgumentException("timeSeconds must be finite");
        WaveSuperpositionParameters parameters = WaveSuperpositionParameters.from(specification, overrides);
        double time = Math.max(0, timeSeconds);
        double x = parameters.probePosition();
        double omega = parameters.angularFrequency();
        double k = parameters.waveNumber();
        double phase1 = k * x - omega * time + parameters.phase1();
        double phase2 = k * x - omega * time + parameters.phase2();
        double displacement = parameters.amplitude1() * Math.cos(phase1) + parameters.amplitude2() * Math.cos(phase2);
        double velocity = omega
                * (parameters.amplitude1() * Math.sin(phase1) + parameters.amplitude2() * Math.sin(phase2));
        return new AnalyticalPoint(Map.of("displacement", displacement, "particleVelocity", velocity,
                "particleAcceleration", -omega * omega * displacement));
    }
}
