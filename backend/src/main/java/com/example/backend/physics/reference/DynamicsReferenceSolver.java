package com.example.backend.physics.reference;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class DynamicsReferenceSolver implements ReferenceSolver {
    private static final double VELOCITY_EPSILON = 1e-10;
    public String solverId() { return "dynamics_reference"; }
    public AnalyticalPoint solve(JsonNode spec, Map<String, Double> overrides, double seconds) {
        String model = PhysicsValues.model(spec);
        if (model.equals("elastic_collision_1d")) return collision(spec, overrides, seconds);
        if (model.equals("spring_1d")) return spring(spec, overrides, seconds);
        if (!model.equals("forces_1d")) throw new IllegalArgumentException("Unsupported dynamics reference model: " + model);
        double mass = positive(PhysicsValues.require(spec, overrides, "mass", "m"));
        double force = PhysicsValues.require(spec, overrides, "net_force", "force", "f");
        double friction = nonNegative(PhysicsValues.require(spec, overrides, "friction_coefficient", "mu", "friction"));
        double gravity = PhysicsValues.gravitationalAcceleration(spec, overrides);
        double frictionForce = friction * mass * gravity;
        double v0 = PhysicsValues.require(spec, overrides, "initial_velocity", "v0", "velocity");
        double x0 = PhysicsValues.require(spec, overrides, "initial_position", "x0", "position");
        double t = Math.max(0, seconds);
        double acceleration = acceleration(force, frictionForce, mass, v0);
        double position;
        double velocity;
        if (Math.abs(v0) > VELOCITY_EPSILON && v0 * acceleration < 0) {
            double stoppingTime = -v0 / acceleration;
            if (t >= stoppingTime) {
                double stoppedPosition = x0 + v0 * stoppingTime + 0.5 * acceleration * stoppingTime * stoppingTime;
                double remaining = t - stoppingTime;
                acceleration = acceleration(force, frictionForce, mass, 0);
                position = stoppedPosition + 0.5 * acceleration * remaining * remaining;
                velocity = acceleration * remaining;
            } else {
                position = x0 + v0 * t + 0.5 * acceleration * t * t;
                velocity = v0 + acceleration * t;
            }
        } else {
            position = x0 + v0 * t + 0.5 * acceleration * t * t;
            velocity = v0 + acceleration * t;
        }
        return new AnalyticalPoint(Map.of("x", position, "vx", velocity, "ax", acceleration,
                "force", mass * acceleration));
    }
    private AnalyticalPoint collision(JsonNode spec, Map<String, Double> overrides, double seconds) {
        double m1 = positive(PhysicsValues.require(spec, overrides, "mass_1", "m1"));
        double m2 = positive(PhysicsValues.require(spec, overrides, "mass_2", "m2"));
        double x1 = PhysicsValues.require(spec, overrides, "initial_position_1", "x1_0", "x10", "x1");
        double x2 = PhysicsValues.require(spec, overrides, "initial_position_2", "x2_0", "x20", "x2");
        double v1 = PhysicsValues.require(spec, overrides, "velocity_1", "v1");
        double v2 = PhysicsValues.require(spec, overrides, "velocity_2", "v2");
        double event = collisionTime(x1, x2, v1, v2);
        double after1 = event > 0 ? ((m1 - m2) * v1 + 2 * m2 * v2) / (m1 + m2) : v1;
        double after2 = event > 0 ? ((m2 - m1) * v2 + 2 * m1 * v1) / (m1 + m2) : v2;
        double t = Math.max(0, seconds);
        double x1Event = x1 + v1 * Math.max(0, event);
        double x2Event = x2 + v2 * Math.max(0, event);
        return new AnalyticalPoint(Map.of("x1", event > 0 && t > event ? x1Event + after1 * (t-event) : x1 + v1*t,
                "x2", event > 0 && t > event ? x2Event + after2 * (t-event) : x2 + v2*t,
                "v1", event > 0 && t >= event ? after1 : v1, "v2", event > 0 && t >= event ? after2 : v2));
    }
    private AnalyticalPoint spring(JsonNode spec, Map<String, Double> overrides, double seconds) {
        double amplitude = positive(PhysicsValues.require(spec, overrides, "amplitude", "a"));
        double mass = positive(PhysicsValues.require(spec, overrides, "mass", "m"));
        double stiffness = positive(PhysicsValues.require(spec, overrides, "spring_constant", "k"));
        double phase = PhysicsValues.require(spec, overrides, "phase", "phi");
        double omega = Math.sqrt(stiffness / mass);
        double angle = omega * seconds + phase;
        double x = amplitude * Math.cos(angle);
        return new AnalyticalPoint(Map.of("x", x, "vx", -amplitude * omega * Math.sin(angle), "ax", -omega * omega * x));
    }

    private double collisionTime(double x1, double x2, double v1, double v2) {
        if (x1 < x2 && v1 > v2) return (x2 - x1) / (v1 - v2);
        if (x1 > x2 && v2 > v1) return (x1 - x2) / (v2 - v1);
        return -1;
    }

    private double positive(double value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Positive quantity required");
        }
        return value;
    }

    private double nonNegative(double value) {
        if (value < 0) throw new IllegalArgumentException("Non-negative quantity required");
        return value;
    }

    private double acceleration(double appliedForce, double frictionForce, double mass, double velocity) {
        if (Math.abs(velocity) <= VELOCITY_EPSILON) {
            if (Math.abs(appliedForce) <= frictionForce) return 0;
            return Math.copySign((Math.abs(appliedForce) - frictionForce) / mass, appliedForce);
        }
        return (appliedForce - Math.copySign(frictionForce, velocity)) / mass;
    }
}
