package com.example.backend.physics.solver.optics;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.optics.RefractionParameters;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RefractionSolver implements PhysicsSolver {
    @Override public String solverId() { return "refraction_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"snell_refraction".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported refraction model: " + PhysicsValues.model(specification));
        RefractionParameters p = RefractionParameters.from(specification, overrides);
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(Math.max(0.01, durationSeconds) / Math.max(0.001, stepSeconds))));
        List<Double> time = new ArrayList<>(points + 1); Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> angle = new ArrayList<>(points + 1), reflected = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) { time.add(Math.min(Math.max(0.01, durationSeconds), i * Math.max(0.001, stepSeconds))); angle.add(p.refractedAngle()); reflected.add(p.totalInternalReflection() ? 1.0 : 0.0); }
        List<Double> reflectedAngle = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) reflectedAngle.add(p.incidentAngle());
        values.put("refractedAngle", angle); values.put("totalInternalReflection", reflected); values.put("reflectedAngle", reflectedAngle);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
