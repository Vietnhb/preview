package com.example.backend.physics.reference.kinematics;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.kinematics.KinematicsParameters;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class KinematicsReferenceSolver implements ReferenceSolver {
    public String solverId() { return "kinematics_reference"; }
    public AnalyticalPoint solve(JsonNode spec, Map<String, Double> overrides, double seconds) {
        KinematicsParameters parameters = KinematicsParameters.from(spec, overrides);
        boolean projectile = parameters.projectile();
        double x0 = parameters.initialPosition();
        double speed = parameters.initialVelocity();
        double t = Math.max(0, seconds);
        if (projectile) {
            double y0 = parameters.initialHeight();
            double angle = parameters.launchAngle();
            double gravity = parameters.gravity();
            double vx = speed * Math.cos(angle);
            double vy0 = speed * Math.sin(angle);
            return new AnalyticalPoint(Map.of("x", x0 + vx * t, "displacement", vx * t, "y", y0 + vy0 * t - .5 * gravity * t * t,
                    "vx", vx, "vy", vy0 - gravity * t, "ax", 0d, "ay", -gravity));
        }
        double acceleration = parameters.acceleration();
        return new AnalyticalPoint(Map.of("x", x0 + speed * t + .5 * acceleration * t * t,
                "displacement", speed * t + .5 * acceleration * t * t,
                "y", 0d, "vx", speed + acceleration * t, "vy", 0d, "ax", acceleration, "ay", 0d));
    }
}
