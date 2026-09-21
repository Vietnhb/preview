package com.example.backend.physics.compatibility.legacy.solver.thermal;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.thermal.ThermalExpansionParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ThermalExpansionSolver implements PhysicsSolver {
    @Override public String solverId() { return "thermal_expansion_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"thermal_expansion".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported thermal-expansion model: " + PhysicsValues.model(specification));
        ThermalExpansionParameters p = ThermalExpansionParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(durationSeconds, stepSeconds);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("deltaTemperature", java.util.Collections.nCopies(time.size(), p.deltaTemperature()));
        values.put("extension", java.util.Collections.nCopies(time.size(), p.extension()));
        values.put("finalLength", java.util.Collections.nCopies(time.size(), p.finalLength()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
