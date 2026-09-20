package com.example.backend.physics.reference.practical;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.practical.ExperimentalGraphParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExperimentalGraphReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "experimental_graph_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"experimental_data_graph".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported experimental graph model: " + PhysicsValues.model(specification));
        }
        ExperimentalGraphParameters p = ExperimentalGraphParameters.from(specification, overrides);
        double x = Math.max(p.xStart(), Math.min(p.xEnd(), timeSeconds));
        return new AnalyticalPoint(Map.of("x", x, "y", p.intercept() + p.slope() * x,
                "slope", p.slope(), "intercept", p.intercept()));
    }
}
