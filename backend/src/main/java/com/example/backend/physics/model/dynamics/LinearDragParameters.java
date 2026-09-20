package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Linear-drag motion with a constant applied force. */
public record LinearDragParameters(double mass, double initialPosition, double initialVelocity,
                                   double constantForce, double dragCoefficient) {
    public static LinearDragParameters from(JsonNode specification, Map<String, Double> overrides) {
        double mass = PhysicsValues.require(specification, overrides, "mass");
        double position = PhysicsValues.require(specification, overrides, "initial_position");
        double velocity = PhysicsValues.require(specification, overrides, "initial_velocity");
        double force = PhysicsValues.require(specification, overrides, "constant_force");
        double drag = PhysicsValues.require(specification, overrides, "drag_coefficient");
        if (!(mass > 0) || !(drag > 0) || !Double.isFinite(position) || !Double.isFinite(velocity)
                || !Double.isFinite(force)) {
            throw new IllegalArgumentException("Linear-drag parameters are invalid");
        }
        return new LinearDragParameters(mass, position, velocity, force, drag);
    }
    public double terminalVelocity() { return constantForce / dragCoefficient; }
    public State stateAt(double time) {
        if (!Double.isFinite(time) || time < 0) throw new IllegalArgumentException("Time must be non-negative");
        double tau = mass / dragCoefficient;
        double vTerminal = terminalVelocity();
        double decay = Math.exp(-time / tau);
        double velocity = vTerminal + (initialVelocity - vTerminal) * decay;
        double position = initialPosition + vTerminal * time + (initialVelocity - vTerminal) * tau * (1 - decay);
        double acceleration = (constantForce - dragCoefficient * velocity) / mass;
        double dragForce = -dragCoefficient * velocity;
        return new State(position, velocity, acceleration, dragForce);
    }
    public record State(double position, double velocity, double acceleration, double dragForce) {}
}
