package com.example.backend.physics.compatibility.legacy.solver.circuits;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.circuits.TransformerParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TransformerSolver implements PhysicsSolver {
    @Override public String solverId() { return "transformer_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"ideal_transformer".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported transformer model: " + PhysicsValues.model(specification));
        TransformerParameters p = TransformerParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("turnsRatio", java.util.Collections.nCopies(time.size(), p.turnsRatio()));
        values.put("secondaryVoltage", java.util.Collections.nCopies(time.size(), p.secondaryVoltage()));
        values.put("primaryCurrent", java.util.Collections.nCopies(time.size(), p.primaryCurrent()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
