package com.example.backend.physics.reference;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class KinematicsReferenceSolver implements ReferenceSolver {
    public String solverId() { return "kinematics_reference"; }
    public AnalyticalPoint solve(JsonNode spec, Map<String, Double> overrides, double seconds) {
        String model = PhysicsValues.model(spec);
        if (!model.equals("uniform_acceleration_1d") && !model.equals("projectile_2d")) throw new IllegalArgumentException("Unsupported kinematics reference model: " + model);
        boolean projectile = model.equals("projectile_2d");
        double x0 = PhysicsValues.require(spec, overrides, "initial_position", "x0", "position");
        double speed = PhysicsValues.require(spec, overrides, "initial_velocity", "v0", "velocity");
        double t = Math.max(0, seconds);
        if (projectile) {
            double y0 = PhysicsValues.require(spec, overrides, "initial_height", "y0", "height");
            double angle = PhysicsValues.require(spec, overrides, "launch_angle", "angle", "theta");
            double gravity = PhysicsValues.gravitationalAcceleration(spec, overrides);
            double vx = speed * Math.cos(angle);
            double vy0 = speed * Math.sin(angle);
            return new AnalyticalPoint(Map.of("x", x0 + vx * t, "y", y0 + vy0 * t - .5 * gravity * t * t,
                    "vx", vx, "vy", vy0 - gravity * t, "ax", 0d, "ay", -gravity));
        }
        double acceleration = PhysicsValues.require(spec, overrides, "acceleration", "a");
        return new AnalyticalPoint(Map.of("x", x0 + speed * t + .5 * acceleration * t * t,
                "y", 0d, "vx", speed + acceleration * t, "vy", 0d, "ax", acceleration, "ay", 0d));
    }
}
