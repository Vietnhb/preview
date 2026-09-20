package com.example.backend.physics.solver.circuits;

import com.example.backend.physics.solver.thermal.TemperatureScaleSolver;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.circuits.ResistorNetworkParameters;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResistorNetworkSolver implements PhysicsSolver {
    @Override public String solverId() { return "resistor_network_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        String model = PhysicsValues.model(specification);
        if (!model.equals("resistors_series") && !model.equals("resistors_parallel")) throw new IllegalArgumentException("Unsupported resistor-network model: " + model);
        ResistorNetworkParameters p = ResistorNetworkParameters.from(specification, overrides);
        boolean parallel = model.equals("resistors_parallel");
        double equivalent = parallel ? p.parallelResistance() : p.seriesResistance();
        double totalCurrent = p.voltage() / equivalent;
        double branchCurrent1 = parallel ? p.voltage() / p.resistance1() : totalCurrent;
        double branchCurrent2 = parallel ? p.voltage() / p.resistance2() : totalCurrent;
        List<Double> time = TemperatureScaleSolver.staticTime(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("equivalentResistance", java.util.Collections.nCopies(time.size(), equivalent));
        values.put("totalCurrent", java.util.Collections.nCopies(time.size(), totalCurrent));
        values.put("branchCurrent1", java.util.Collections.nCopies(time.size(), branchCurrent1));
        values.put("branchCurrent2", java.util.Collections.nCopies(time.size(), branchCurrent2));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
