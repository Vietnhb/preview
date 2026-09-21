package com.example.backend.physics.compatibility.legacy.solver.modern;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.modern.RadiationSafetyParameters;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RadiationSafetySolver implements PhysicsSolver {
    @Override public String solverId() { return "radiation_safety_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"radiation_safety".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported radiation-safety model: " + PhysicsValues.model(specification));
        RadiationSafetyParameters p = RadiationSafetyParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("doseRate", java.util.Collections.nCopies(time.size(), p.doseRate()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
