package com.example.backend.physics.reference.circuits;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class CircuitReferenceSolver implements ReferenceSolver {
    public String solverId() { return "circuit_reference"; }
    public AnalyticalPoint solve(JsonNode spec, Map<String, Double> overrides, double seconds) {
        String model = PhysicsValues.model(spec);
        if (!model.equals("rc_charging") && !model.equals("rc_discharging")) throw new IllegalArgumentException("Unsupported circuit reference model: " + model);
        boolean discharging = model.equals("rc_discharging");
        double voltage = PhysicsValues.require(spec, overrides, "voltage");
        double resistance = positive(PhysicsValues.require(spec, overrides, "resistance"));
        double capacitance = positive(PhysicsValues.require(spec, overrides, "capacitance"));
        double decay = Math.exp(-Math.max(0, seconds) / (resistance * capacitance));
        double capacitorVoltage = discharging ? voltage * decay : voltage * (1 - decay);
        double current = (discharging ? -voltage : voltage) / resistance * decay;
        return new AnalyticalPoint(Map.of("voltage", capacitorVoltage, "current", current));
    }
    private double positive(double value) {
        if (value <= 0) {
            throw new IllegalArgumentException("R and C must be positive");
        }
        return value;
    }
}
