package com.example.backend.physics.compatibility.legacy.solver.electromagnetism;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.electromagnetism.MagneticForceParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MagneticForceSolver implements PhysicsSolver {
    @Override public String solverId() { return "magnetic_force_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double durationSeconds, double stepSeconds) {
        if (!"magnetic_force".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported magnetic-force model: " + PhysicsValues.model(specification));
        MagneticForceParameters p = MagneticForceParameters.from(specification, overrides);
        int points = Math.clamp((int) Math.ceil(Math.max(.01, durationSeconds) / Math.max(.001, stepSeconds)),
                1, 16_384);
        List<Double> time = new ArrayList<>(points + 1);
        List<Double> force = new ArrayList<>(points + 1);
        for(int i=0;i<=points;i++){time.add(Math.clamp(i*Math.max(.001,stepSeconds), 0, Math.max(.01,durationSeconds)));force.add(p.forceMagnitude());}
        Map<String,List<Double>> values = new LinkedHashMap<>(); values.put("magneticForce",force);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
