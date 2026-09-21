package com.example.backend.physics.compatibility.legacy.solver.thermal;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.thermal.PhaseChangeParameters;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact piecewise heating curve for phase changes at constant pressure. */
@Component
public class PhaseChangeSolver implements PhysicsSolver {
    @Override public String solverId() { return "phase_change_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"phase_change".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported phase-change model: " + PhysicsValues.model(specification));
        }
        PhaseChangeParameters p = PhaseChangeParameters.from(specification, overrides);
        if (!(durationSeconds > 0) || !(stepSeconds > 0) || !Double.isFinite(durationSeconds + stepSeconds)) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(durationSeconds / stepSeconds)));
        List<Double> time = new ArrayList<>(points + 1);
        List<Double> heat = new ArrayList<>(points + 1);
        List<Double> temperature = new ArrayList<>(points + 1);
        List<Double> liquid = new ArrayList<>(points + 1);
        List<Double> vapor = new ArrayList<>(points + 1);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        for (int i = 0; i <= points; i++) {
            double t = Math.min(durationSeconds, i * stepSeconds);
            double q = p.heatingPower() * t;
            PhaseChangeParameters.State state = p.stateAtEnergy(q);
            time.add(t); heat.add(q); temperature.add(state.temperature());
            liquid.add(state.liquidFraction()); vapor.add(state.vaporFraction());
        }
        values.put("temperature", temperature);
        values.put("heatAdded", heat);
        values.put("liquidFraction", liquid);
        values.put("vaporFraction", vapor);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
