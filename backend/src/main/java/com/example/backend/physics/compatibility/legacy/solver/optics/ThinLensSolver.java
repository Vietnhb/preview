package com.example.backend.physics.compatibility.legacy.solver.optics;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.optics.ThinLensParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates the paraxial thin-lens equation as a static timeseries. */
@Component
public class ThinLensSolver implements PhysicsSolver {
    @Override public String solverId() { return "thin_lens_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"thin_lens_imaging".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported thin-lens model: " + PhysicsValues.model(specification));
        }
        ThinLensParameters parameters = ThinLensParameters.from(specification, overrides);
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0 || !Double.isFinite(stepSeconds) || stepSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        int points = Math.clamp((int) Math.ceil(durationSeconds / stepSeconds), 1, 16_384);
        List<Double> time = new ArrayList<>(points + 1);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> imageDistance = new ArrayList<>(points + 1);
        List<Double> imageHeight = new ArrayList<>(points + 1);
        List<Double> magnification = new ArrayList<>(points + 1);
        for (int index = 0; index <= points; index++) {
            time.add(Math.min(durationSeconds, index * stepSeconds));
            imageDistance.add(parameters.imageDistance()); imageHeight.add(parameters.imageHeight());
            magnification.add(parameters.magnification());
        }
        values.put("imageDistance", imageDistance); values.put("imageHeight", imageHeight); values.put("magnification", magnification);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
