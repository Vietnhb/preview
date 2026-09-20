package com.example.backend.physics.solver.practical;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.practical.ExperimentalGraphParameters;
import com.example.backend.physics.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExperimentalGraphSolver implements PhysicsSolver {
    @Override public String solverId() { return "experimental_graph_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"experimental_data_graph".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported experimental graph model: " + PhysicsValues.model(specification));
        }
        ExperimentalGraphParameters p = ExperimentalGraphParameters.from(specification, overrides);
        List<Double> time = new ArrayList<>(p.sampleCount());
        List<Double> x = new ArrayList<>(p.sampleCount());
        List<Double> y = new ArrayList<>(p.sampleCount());
        for (int i = 0; i < p.sampleCount(); i++) {
            double independent = p.xAt(i);
            time.add(independent);
            x.add(independent);
            y.add(p.yAt(i));
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x", x);
        values.put("y", y);
        values.put("slope", java.util.Collections.nCopies(p.sampleCount(), p.slope()));
        values.put("intercept", java.util.Collections.nCopies(p.sampleCount(), p.intercept()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
