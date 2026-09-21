package com.example.backend.physics.compatibility.legacy.model.dynamics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Uniform circular motion in a horizontal/vertical idealized plane. */
public record CircularMotionParameters(double mass, double radius, double speed) {
    public static CircularMotionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double mass = PhysicsValues.require(specification, overrides, "mass");
        double radius = PhysicsValues.require(specification, overrides, "radius");
        double speed = PhysicsValues.require(specification, overrides, "speed");
        if (!(mass > 0 && radius > 0 && speed > 0)) throw new IllegalArgumentException("Mass, radius and speed are invalid");
        return new CircularMotionParameters(mass, radius, speed);
    }
    public double angularSpeed() { return speed / radius; }
    public double period() { return 2 * Math.PI * radius / speed; }
    public double frequency() { return speed / (2 * Math.PI * radius); }
    public double centripetalAcceleration() { return speed * speed / radius; }
    public double centripetalForce() { return mass * centripetalAcceleration(); }
}
