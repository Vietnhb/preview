package com.example.backend.physics.solver.dynamics;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.dynamics.MomentEquilibriumParameters;
import com.example.backend.physics.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MomentEquilibriumSolver implements PhysicsSolver {
    @Override public String solverId() { return "moment_equilibrium_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"moment_equilibrium".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported moment model: " + PhysicsValues.model(specification));
        }
        MomentEquilibriumParameters p = MomentEquilibriumParameters.from(specification, overrides);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("moment1", Collections.singletonList(p.moment1()));
        values.put("moment2", Collections.singletonList(p.moment2()));
        values.put("netMoment", Collections.singletonList(p.netMoment()));
        values.put("equilibriumResidual", Collections.singletonList(p.equilibriumResidual()));
        return new SolverOutput(List.of(0d), Map.of(), Map.of(), Map.of(), values);
    }
}
