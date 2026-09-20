package com.example.backend.physics.solver.waves;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.waves.WaterSurfaceInterferenceParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Two coherent point sources on a bounded, uniformly sampled water surface. */
@Component
public class WaterSurfaceInterferenceSolver implements PhysicsSolver {
    @Override public String solverId() { return "water_surface_interference_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"water_surface_interference".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported water-surface model: " + PhysicsValues.model(specification));
        }
        WaterSurfaceInterferenceParameters p = WaterSurfaceInterferenceParameters.from(specification, overrides);
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0 || !Double.isFinite(stepSeconds) || stepSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        int n = p.spatialSamples();
        int maxTimeSamplesForGrid = Math.max(2, ScalarField.MAX_CELLS / (n * n));
        int timeSamples = Math.min(Math.min(256, maxTimeSamplesForGrid),
                Math.max(2, (int) Math.ceil(durationSeconds / stepSeconds) + 1));
        double actualStep = durationSeconds / (timeSamples - 1);
        double dx = p.domainSize() / (n - 1);
        List<Double> x = new ArrayList<>(n), y = new ArrayList<>(n), time = new ArrayList<>(timeSamples);
        for (int i = 0; i < n; i++) { x.add(-p.domainSize() / 2.0 + i * dx); y.add(-p.domainSize() / 2.0 + i * dx); }
        for (int i = 0; i < timeSamples; i++) time.add(i * actualStep);
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> centerHeight = new ArrayList<>(timeSamples);
        for (double t : time) {
            List<Double> row = new ArrayList<>(n * n);
            for (double xi : x) for (double yi : y) row.add(height(p, xi, yi, t));
            rows.add(row);
            centerHeight.add(height(p, 0, 0, t));
        }
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 2,
                List.of(new ScalarField.Axis("x", "m", x), new ScalarField.Axis("y", "m", y)),
                List.of(time.size(), n, n), time, rows, "m", "s",
                new ScalarField.Sampling(dx, actualStep), "linear", "open;water_surface;point_sources=2");
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), Map.of("centerHeight", centerHeight), Map.of("waterSurface", field));
    }

    static double height(WaterSurfaceInterferenceParameters p, double x, double y, double time) {
        double half = p.sourceSeparation() / 2.0;
        double r1 = Math.hypot(x, y - half);
        double r2 = Math.hypot(x, y + half);
        return p.amplitude() * (Math.cos(p.waveNumber() * r1 - p.angularFrequency() * time)
                + Math.cos(p.waveNumber() * r2 - p.angularFrequency() * time));
    }
}
