package com.example.backend.physics.solver.thermal;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.thermal.AdiabaticGasParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdiabaticGasSolver implements PhysicsSolver {
    @Override public String solverId() { return "adiabatic_gas_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"adiabatic_gas".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported adiabatic-gas model: " + PhysicsValues.model(specification));
        AdiabaticGasParameters p = AdiabaticGasParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("initialPressure", java.util.Collections.nCopies(time.size(), p.initialPressure()));
        values.put("finalPressure", java.util.Collections.nCopies(time.size(), p.finalPressure()));
        values.put("finalTemperature", java.util.Collections.nCopies(time.size(), p.finalTemperature()));
        values.put("work", java.util.Collections.nCopies(time.size(), p.workByGas()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
