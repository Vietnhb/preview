package com.example.backend.physics.reference.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.dynamics.LinearDragParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LinearDragReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "linear_drag_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"linear_drag_motion".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported drag model: " + PhysicsValues.model(specification));
        }
        LinearDragParameters.State state = LinearDragParameters.from(specification, overrides).stateAt(timeSeconds);
        return new AnalyticalPoint(Map.of("position", state.position(), "velocity", state.velocity(),
                "acceleration", state.acceleration(), "dragForce", state.dragForce()));
    }
}
