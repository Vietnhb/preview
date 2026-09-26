package com.example.backend.physics.compatibility.legacy.solver.circuits;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.circuits.AcRlcParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AcRlcSolver implements PhysicsSolver {
    @Override public String solverId() { return "ac_rlc_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"ac_rlc_circuit".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported AC RLC model: " + PhysicsValues.model(specification));
        AcRlcParameters p = AcRlcParameters.from(specification, overrides);
        int points = Math.clamp((int) Math.ceil(Math.max(0.01, durationSeconds) / Math.max(0.001, stepSeconds)),
                1, 16_384);
        List<Double> time = new ArrayList<>(points + 1);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> impedance = new ArrayList<>(points + 1);
        List<Double> current = new ArrayList<>(points + 1);
        List<Double> factor = new ArrayList<>(points + 1);
        List<Double> power = new ArrayList<>(points + 1);
        List<Double> phase = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) { time.add(Math.clamp(i * Math.max(0.001, stepSeconds), 0, Math.max(0.01, durationSeconds))); impedance.add(p.impedance()); current.add(p.rmsCurrent()); factor.add(p.powerFactor()); power.add(p.realPower()); phase.add(p.phase()); }
        values.put("impedance", impedance); values.put("rmsCurrent", current); values.put("powerFactor", factor); values.put("realPower", power); values.put("phase", phase);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
