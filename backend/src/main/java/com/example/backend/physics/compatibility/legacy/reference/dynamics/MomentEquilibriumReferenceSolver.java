package com.example.backend.physics.compatibility.legacy.reference.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.dynamics.MomentEquilibriumParameters;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MomentEquilibriumReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "moment_equilibrium_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"moment_equilibrium".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported moment model: " + PhysicsValues.model(specification));
        }
        MomentEquilibriumParameters p = MomentEquilibriumParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("moment1", p.moment1(), "moment2", p.moment2(),
                "netMoment", p.netMoment(), "equilibriumResidual", p.equilibriumResidual()));
    }
}
