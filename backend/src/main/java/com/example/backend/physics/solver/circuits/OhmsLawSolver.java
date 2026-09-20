package com.example.backend.physics.solver.circuits;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.circuits.OhmsLawParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OhmsLawSolver implements PhysicsSolver {
    @Override public String solverId() { return "ohms_law_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"ohms_law".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported Ohm-law model: " + PhysicsValues.model(specification));
        OhmsLawParameters p = OhmsLawParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("voltage", java.util.Collections.nCopies(time.size(), p.voltage()));
        values.put("resistance", java.util.Collections.nCopies(time.size(), p.resistance()));
        values.put("current", java.util.Collections.nCopies(time.size(), p.current()));
        values.put("power", java.util.Collections.nCopies(time.size(), p.power()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
