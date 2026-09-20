package com.example.backend.physics.solver.circuits;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CircuitSolver implements PhysicsSolver {
    @Override
    public String solverId() { return "circuit_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        if (!model.equals("rc_charging") && !model.equals("rc_discharging")) throw new IllegalArgumentException("Unsupported circuit model: " + model);
        boolean discharging = model.equals("rc_discharging");
        double voltage = PhysicsValues.require(specification, overrides, "voltage");
        double resistance = positive(PhysicsValues.require(specification, overrides, "resistance"));
        double capacitance = positive(PhysicsValues.require(specification, overrides, "capacitance"));
        double tau = resistance * capacitance;
        double duration = Math.max(0.01, durationSeconds);
        double step = Math.clamp(stepSeconds, 0.001, 0.2);
        List<Double> times = new ArrayList<>();
        List<Double> voltages = new ArrayList<>();
        List<Double> currents = new ArrayList<>();
        int points = Math.max(1, (int) Math.ceil(duration / step));
        for (int i = 0; i <= points; i++) {
            double t = Math.min(duration, i * step);
            double normalized = Math.exp(-t / tau);
            double capacitorVoltage = discharging ? voltage * normalized : voltage * (1 - normalized);
            double current = discharging ? -(voltage / resistance) * normalized : (voltage / resistance) * normalized;
            times.add(t);
            voltages.add(capacitorVoltage);
            currents.add(current);
        }
        return new SolverOutput(times, Map.of(), Map.of(), Map.of(),
                Map.of("voltage", voltages, "current", currents));
    }

    private static double positive(double value) {
        if (value <= 0) throw new IllegalArgumentException("Positive quantity required");
        return value;
    }
}
