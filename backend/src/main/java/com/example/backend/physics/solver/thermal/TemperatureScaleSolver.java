package com.example.backend.physics.solver.thermal;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.thermal.TemperatureScaleParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TemperatureScaleSolver implements PhysicsSolver {
    @Override public String solverId() { return "temperature_scale_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"temperature_scales".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported temperature-scale model: " + PhysicsValues.model(specification));
        TemperatureScaleParameters p = TemperatureScaleParameters.from(specification, overrides);
        List<Double> time = staticTime(durationSeconds, stepSeconds);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("celsius", constant(time.size(), p.celsius()));
        values.put("kelvin", constant(time.size(), p.kelvin()));
        values.put("fahrenheit", constant(time.size(), p.fahrenheit()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
    public static List<Double> staticTime(double duration, double step) {
        if (!Double.isFinite(duration) || duration <= 0 || !Double.isFinite(step) || step <= 0)
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        int count = Math.min(16_384, Math.max(1, (int) Math.ceil(duration / step)));
        List<Double> time = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) time.add(Math.min(duration, i * step));
        return time;
    }
    private static List<Double> constant(int size, double value) { return java.util.Collections.nCopies(size, value); }
}
