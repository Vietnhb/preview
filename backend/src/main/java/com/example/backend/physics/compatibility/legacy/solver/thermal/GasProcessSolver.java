package com.example.backend.physics.compatibility.legacy.solver.thermal;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.thermal.GasProcessParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ideal-gas isobaric/isochoric process solver. */
@Component
public class GasProcessSolver implements PhysicsSolver {
    @Override public String solverId() { return "gas_process_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        if (!model.equals("ideal_gas_isobaric") && !model.equals("ideal_gas_isochoric"))
            throw new IllegalArgumentException("Unsupported gas-process model: " + model);
        GasProcessParameters p = GasProcessParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(durationSeconds, stepSeconds);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        put(values, time, "initialPressure", p.initialPressure());
        put(values, time, "initialVolume", p.initialVolume());
        put(values, time, "initialTemperature", p.initialTemperature());
        put(values, time, "finalTemperature", p.finalTemperature());
        if (model.equals("ideal_gas_isobaric")) {
            put(values, time, "finalPressure", p.initialPressure());
            put(values, time, "finalVolume", p.isobaricFinalVolume());
            put(values, time, "work", p.isobaricWork());
        } else {
            put(values, time, "finalPressure", p.isochoricFinalPressure());
            put(values, time, "finalVolume", p.initialVolume());
            put(values, time, "work", 0);
        }
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
    private static void put(Map<String, List<Double>> values, List<Double> time, String key, double value) {
        values.put(key, Collections.nCopies(time.size(), value));
    }
}
