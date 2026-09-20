package com.example.backend.physics.solver.circuits;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.circuits.CapacitorParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CapacitorSolver implements PhysicsSolver {
    @Override public String solverId() { return "capacitor_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"capacitor_basic".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported capacitor model: " + PhysicsValues.model(specification));
        CapacitorParameters p = CapacitorParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("charge", java.util.Collections.nCopies(time.size(), p.charge()));
        values.put("energy", java.util.Collections.nCopies(time.size(), p.energy()));
        values.put("voltage", java.util.Collections.nCopies(time.size(), p.voltage()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
