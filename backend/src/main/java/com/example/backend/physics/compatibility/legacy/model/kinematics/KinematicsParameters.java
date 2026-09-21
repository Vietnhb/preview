package com.example.backend.physics.compatibility.legacy.model.kinematics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical parameters shared by the kinematics schemas. */
public record KinematicsParameters(
        String model,
        double initialPosition,
        double initialVelocity,
        double acceleration,
        double initialHeight,
        double launchAngle,
        double gravity,
        boolean projectile) {

    public static KinematicsParameters from(JsonNode specification, Map<String, Double> overrides) {
        String model = PhysicsValues.model(specification);
        if (!model.equals("uniform_acceleration") && !model.equals("projectile")
                && !model.equals("position_time_graph") && !model.equals("velocity_time_graph")
                && !model.equals("acceleration_time_graph")) {
            throw new IllegalArgumentException("Unsupported kinematics model: " + model);
        }
        double x0 = PhysicsValues.require(specification, overrides, "initial_position");
        double velocity = PhysicsValues.require(specification, overrides, "initial_velocity");
        boolean projectile = model.equals("projectile");
        if (projectile) {
            double y0 = PhysicsValues.require(specification, overrides, "initial_height");
            double angle = PhysicsValues.require(specification, overrides, "launch_angle");
            double gravity = PhysicsValues.gravitationalAcceleration(specification, overrides);
            return new KinematicsParameters(model, x0, velocity, 0, y0, angle, gravity, true);
        }
        double acceleration = PhysicsValues.require(specification, overrides, "acceleration");
        return new KinematicsParameters(model, x0, velocity, acceleration, 0, 0, 0, false);
    }
}
