package com.example.backend.physics.solver.modern;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.modern.NuclearReactionParameters;
import com.example.backend.physics.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NuclearReactionSolver implements PhysicsSolver {
    @Override public String solverId() { return "nuclear_reaction_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"nuclear_reaction_energy".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported nuclear reaction model: " + PhysicsValues.model(specification));
        }
        NuclearReactionParameters p = NuclearReactionParameters.from(specification, overrides);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("massDefect", Collections.singletonList(p.massDefect()));
        values.put("releasedEnergyPerReaction", Collections.singletonList(p.releasedEnergyPerReaction()));
        values.put("totalReleasedEnergy", Collections.singletonList(p.totalReleasedEnergy()));
        return new SolverOutput(List.of(0d), Map.of(), Map.of(), Map.of(), values);
    }
}
