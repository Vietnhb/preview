package com.example.backend.physics.compatibility.legacy.solver.optics;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.optics.LightInterferenceParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LightInterferenceSolver implements PhysicsSolver {
    @Override public String solverId() { return "light_interference_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"light_interference".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported light-interference model: " + PhysicsValues.model(specification));
        LightInterferenceParameters p = LightInterferenceParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("phaseDifference", java.util.Collections.nCopies(time.size(), p.phaseDifference()));
        values.put("intensity", java.util.Collections.nCopies(time.size(), p.intensity()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
