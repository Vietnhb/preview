package com.example.backend.physics.solver.thermal;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.thermal.CalorimetryParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ideal isolated mixing/calorimetry; each sample reports the equilibrium state. */
@Component
public class CalorimetrySolver implements PhysicsSolver {
    @Override public String solverId() { return "calorimetry_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"calorimetry_mixing".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported calorimetry model: " + PhysicsValues.model(specification));
        CalorimetryParameters p = CalorimetryParameters.from(specification, overrides);
        if (!(durationSeconds > 0) || !(stepSeconds > 0) || !Double.isFinite(durationSeconds + stepSeconds))
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(durationSeconds / stepSeconds)));
        List<Double> time = new ArrayList<>(points + 1), t1 = new ArrayList<>(points + 1), t2 = new ArrayList<>(points + 1);
        List<Double> heat1 = new ArrayList<>(points + 1), heat2 = new ArrayList<>(points + 1), eq = new ArrayList<>(points + 1);
        double equilibrium = p.equilibriumTemperature();
        for (int i = 0; i <= points; i++) {
            double t = Math.min(durationSeconds, i * stepSeconds);
            time.add(t); t1.add(equilibrium); t2.add(equilibrium);
            heat1.add(p.mass1() * p.specificHeat1() * (t1.get(i) - p.initialTemperature1()));
            heat2.add(p.mass2() * p.specificHeat2() * (t2.get(i) - p.initialTemperature2())); eq.add(equilibrium);
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("temperature1", t1); values.put("temperature2", t2); values.put("equilibriumTemperature", eq);
        values.put("heat1", heat1); values.put("heat2", heat2);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
