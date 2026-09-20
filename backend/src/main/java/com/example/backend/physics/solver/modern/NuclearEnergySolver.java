package com.example.backend.physics.solver.modern;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.modern.NuclearEnergyParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NuclearEnergySolver implements PhysicsSolver {
    @Override public String solverId() { return "nuclear_energy_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"nuclear_energy".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported nuclear-energy model: " + PhysicsValues.model(specification));
        NuclearEnergyParameters p = NuclearEnergyParameters.from(specification, overrides);
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(Math.max(0.01, durationSeconds) / Math.max(0.001, stepSeconds))));
        List<Double> time = new ArrayList<>(points + 1), energy = new ArrayList<>(points + 1), mass = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) { time.add(Math.min(Math.max(0.01, durationSeconds), i * Math.max(0.001, stepSeconds))); energy.add(p.releasedEnergy()); mass.add(p.massDefect()); }
        Map<String, List<Double>> values = new LinkedHashMap<>(); values.put("releasedEnergy", energy); values.put("massDefect", mass);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
