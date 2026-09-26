package com.example.backend.physics.compatibility.legacy.reference.waves;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.waves.WaterSurfaceInterferenceParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent point evaluator for the two-source water-surface model. */
@Component
public class WaterSurfaceInterferenceReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "water_surface_interference_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"water_surface_interference".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported water-surface model: " + PhysicsValues.model(specification));
        }
        WaterSurfaceInterferenceParameters p = WaterSurfaceInterferenceParameters.from(specification, overrides);
        double half = p.sourceSeparation() / 2.0;
        double r1 = Math.hypot(0, -half);
        double r2 = Math.hypot(0, half);
        double center = p.amplitude() * (Math.cos(p.waveNumber() * r1 - p.angularFrequency() * timeSeconds)
                + Math.cos(p.waveNumber() * r2 - p.angularFrequency() * timeSeconds));
        return new AnalyticalPoint(Map.of("centerHeight", center));
    }
}
