package com.example.backend.physics.compatibility.legacy.solver.electromagnetism;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.electromagnetism.UniformElectricFieldParameters;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class UniformElectricFieldSolver implements PhysicsSolver {
    @Override public String solverId() { return "uniform_electric_field_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"uniform_electric_field".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported uniform-field model: " + PhysicsValues.model(specification));
        }
        UniformElectricFieldParameters p = UniformElectricFieldParameters.from(specification, overrides);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("fieldStrength", Collections.singletonList(p.fieldStrength()));
        values.put("electricForce", Collections.singletonList(p.electricForce()));
        values.put("acceleration", Collections.singletonList(p.acceleration()));
        values.put("transverseDisplacement", Collections.singletonList(p.transverseDisplacement()));
        values.put("longitudinalDisplacement", Collections.singletonList(p.longitudinalDisplacement()));
        return new SolverOutput(List.of(0d), Map.of(), Map.of(), Map.of(), values);
    }
}
