package com.example.backend.physics.compatibility.legacy.solver.dynamics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.dynamics.DampedForcedOscillationParameters;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numerical sampling of the exact forced oscillator solution (under/critical/over damping). */
@Component
public class AdvancedOscillationSolver implements PhysicsSolver {
    @Override public String solverId() { return "advanced_oscillation_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"damped_forced_oscillation".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported advanced oscillation model: "
                    + PhysicsValues.model(specification));
        }
        DampedForcedOscillationParameters p = DampedForcedOscillationParameters.from(
                PhysicsValues.bag(specification, overrides));
        if (!(durationSeconds > 0) || !(stepSeconds > 0)
                || !Double.isFinite(durationSeconds + stepSeconds)) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(durationSeconds / stepSeconds)));
        List<Double> time = new ArrayList<>(points + 1);
        List<Double> displacement = new ArrayList<>(points + 1);
        List<Double> velocity = new ArrayList<>(points + 1);
        List<Double> acceleration = new ArrayList<>(points + 1);
        List<Double> energy = new ArrayList<>(points + 1);
        List<Double> drive = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) {
            double t = Math.min(durationSeconds, i * stepSeconds);
            DampedForcedOscillationParameters.State state = p.stateAt(t);
            time.add(t);
            displacement.add(state.displacement());
            velocity.add(state.velocity());
            acceleration.add(state.acceleration());
            energy.add(0.5 * p.mass() * state.velocity() * state.velocity()
                    + 0.5 * p.springConstant() * state.displacement() * state.displacement());
            drive.add(p.drivingAmplitude() * Math.cos(p.drivingFrequency() * t));
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", displacement);
        values.put("velocity", velocity);
        values.put("acceleration", acceleration);
        values.put("mechanicalEnergy", energy);
        values.put("drivingForce", drive);
        return new SolverOutput(time, Map.of("x", displacement), Map.of("x", velocity),
                Map.of("x", acceleration), values);
    }
}
