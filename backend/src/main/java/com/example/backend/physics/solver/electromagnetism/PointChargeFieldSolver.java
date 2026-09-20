package com.example.backend.physics.solver.electromagnetism;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.electromagnetism.PointChargeFieldParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PointChargeFieldSolver implements PhysicsSolver {
    @Override public String solverId() { return "electric_field_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double durationSeconds, double stepSeconds) {
        if (!"point_charge_field".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported electric-field model: " + PhysicsValues.model(specification));
        PointChargeFieldParameters p = PointChargeFieldParameters.from(specification, overrides);
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(Math.max(.01, durationSeconds) / Math.max(.001, stepSeconds))));
        List<Double> time = new ArrayList<>(points + 1), field = new ArrayList<>(points + 1), potential = new ArrayList<>(points + 1);
        for (int i=0;i<=points;i++){time.add(Math.min(Math.max(.01,durationSeconds),i*Math.max(.001,stepSeconds)));field.add(p.electricFieldMagnitude());potential.add(p.electricPotential());}
        Map<String,List<Double>> values = new LinkedHashMap<>(); values.put("electricField",field); values.put("electricPotential",potential);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
