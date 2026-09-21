package com.example.backend.physics.compatibility.legacy.solver.dynamics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.dynamics.LinearDragParameters;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LinearDragSolver implements PhysicsSolver {
    @Override public String solverId() { return "linear_drag_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"linear_drag_motion".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported drag model: " + PhysicsValues.model(specification));
        }
        LinearDragParameters p = LinearDragParameters.from(specification, overrides);
        if (!(durationSeconds > 0) || !(stepSeconds > 0)) throw new IllegalArgumentException("Duration and step must be positive");
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(durationSeconds / stepSeconds)));
        List<Double> time = new ArrayList<>(points + 1), position = new ArrayList<>(points + 1);
        List<Double> velocity = new ArrayList<>(points + 1), acceleration = new ArrayList<>(points + 1), drag = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) {
            double t = Math.min(durationSeconds, i * stepSeconds);
            LinearDragParameters.State state = p.stateAt(t);
            time.add(t); position.add(state.position()); velocity.add(state.velocity());
            acceleration.add(state.acceleration()); drag.add(state.dragForce());
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("position", position); values.put("velocity", velocity); values.put("acceleration", acceleration); values.put("dragForce", drag);
        return new SolverOutput(time, Map.of("x", position), Map.of("x", velocity), Map.of("x", acceleration), values);
    }
}
