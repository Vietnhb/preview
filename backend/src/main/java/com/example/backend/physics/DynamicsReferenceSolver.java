package com.example.backend.physics;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class DynamicsReferenceSolver implements ReferenceSolver {
    private static final double GRAVITY = 9.81;
    public String solverId() { return "dynamics_reference"; }
    public AnalyticalPoint solve(JsonNode spec, Map<String, Double> overrides, double seconds) {
        String model = PhysicsValues.model(spec);
        if (model.equals("elastic_collision_1d")) return collision(spec, overrides, seconds);
        if (model.equals("spring_1d")) return spring(spec, overrides, seconds);
        if (!model.equals("forces_1d")) throw new IllegalArgumentException("Unsupported dynamics reference model: " + model);
        double mass = positive(PhysicsValues.require(spec, overrides, "mass", "m"));
        double force = PhysicsValues.require(spec, overrides, "net_force", "force", "f");
        double friction = PhysicsValues.require(spec, overrides, "friction_coefficient", "mu", "friction");
        double v0 = PhysicsValues.require(spec, overrides, "initial_velocity", "v0", "velocity");
        double x0 = PhysicsValues.require(spec, overrides, "initial_position", "x0", "position");
        double acceleration = force / mass - friction * GRAVITY;
        double t = Math.max(0, seconds);
        return new AnalyticalPoint(Map.of("x", x0 + v0 * t + .5 * acceleration * t * t,
                "vx", v0 + acceleration * t, "ax", acceleration, "force", force - friction * mass * GRAVITY));
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
}
