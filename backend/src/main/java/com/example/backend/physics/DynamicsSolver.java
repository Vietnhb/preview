package com.example.backend.physics;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DynamicsSolver implements PhysicsSolver {
    private static final double GRAVITY = 9.81;

    @Override
    public String solverId() { return "dynamics_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        if (model.equals("elastic_collision_1d")) {
            return solveCollision(specification, overrides, durationSeconds, stepSeconds);
        }
        if (model.equals("spring_1d")) {
            return solveOscillation(specification, overrides, durationSeconds, stepSeconds);
        }
        if (model.equals("forces_1d")) return solveForces(specification, overrides, durationSeconds, stepSeconds);
        throw new IllegalArgumentException("Unsupported dynamics model: " + model);
    }

    private SolverOutput solveForces(JsonNode specification, Map<String, Double> overrides,
                                     double duration, double step) {
        double mass = positive(PhysicsValues.require(specification, overrides, "mass", "m"));
        double force = PhysicsValues.require(specification, overrides, "net_force", "force", "f");
        double friction = Math.max(0, PhysicsValues.require(specification, overrides, "friction_coefficient", "mu", "friction"));
        double acceleration = force / mass - friction * GRAVITY;
        double velocity = PhysicsValues.require(specification, overrides, "initial_velocity", "v0", "velocity");
        double position = PhysicsValues.require(specification, overrides, "initial_position", "x0", "position");
        List<Double> times = new ArrayList<>();
        List<Double> x = new ArrayList<>();
        List<Double> vx = new ArrayList<>();
        List<Double> ax = new ArrayList<>();
        List<Double> netForce = new ArrayList<>();
        int points = Math.max(1, (int) Math.ceil(Math.max(0.01, duration) / Math.max(0.001, step)));
        for (int i = 0; i <= points; i++) {
            double t = Math.min(Math.max(0.01, duration), i * Math.max(0.001, step));
            times.add(t);
            x.add(position);
            vx.add(velocity);
            ax.add(acceleration);
            netForce.add(force - friction * mass * GRAVITY);
            if (i == points) break;
            double dt = Math.min(Math.max(0.001, step), Math.max(0.01, duration) - t);
            position += velocity * dt + 0.5 * acceleration * dt * dt;
            velocity += acceleration * dt;
        }
        Map<String, List<Double>> positions = new LinkedHashMap<>();
        positions.put("x", x);
        Map<String, List<Double>> velocities = new LinkedHashMap<>();
        velocities.put("x", vx);
        Map<String, List<Double>> accelerations = new LinkedHashMap<>();
        accelerations.put("x", ax);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x", x);
        values.put("vx", vx);
        values.put("ax", ax);
        values.put("force", netForce);
        return new SolverOutput(times, positions, velocities, accelerations, values);
    }

    private SolverOutput solveCollision(JsonNode specification, Map<String, Double> overrides,
                                        double duration, double step) {
        double m1 = positive(PhysicsValues.require(specification, overrides, "mass_1", "m1"));
        double m2 = positive(PhysicsValues.require(specification, overrides, "mass_2", "m2"));
        double x1_0 = PhysicsValues.require(specification, overrides, "initial_position_1", "x1_0", "x10", "x1");
        double x2_0 = PhysicsValues.require(specification, overrides, "initial_position_2", "x2_0", "x20", "x2");
        double v1 = PhysicsValues.require(specification, overrides, "velocity_1", "v1");
        double v2 = PhysicsValues.require(specification, overrides, "velocity_2", "v2");

        double collisionAt = calculateCollisionTime(x1_0, x2_0, v1, v2, overrides, specification);
        boolean willCollide = collisionAt > 0;
        double afterV1 = willCollide ? ((m1 - m2) * v1 + 2 * m2 * v2) / (m1 + m2) : v1;
        double afterV2 = willCollide ? ((m2 - m1) * v2 + 2 * m1 * v1) / (m1 + m2) : v2;
        double x1Coll = willCollide ? (x1_0 + v1 * collisionAt) : 0;
        double x2Coll = willCollide ? (x2_0 + v2 * collisionAt) : 0;

        List<Double> times = new ArrayList<>();
        List<Double> x1 = new ArrayList<>();
        List<Double> x2 = new ArrayList<>();
        List<Double> v1Series = new ArrayList<>();
        List<Double> v2Series = new ArrayList<>();
        int points = Math.max(1, (int) Math.ceil(Math.max(0.01, duration) / Math.max(0.001, step)));
        for (int i = 0; i <= points; i++) {
            double t = Math.min(Math.max(0.01, duration), i * Math.max(0.001, step));
            times.add(t);
            double currentX1 = (!willCollide || t <= collisionAt) ? (x1_0 + v1 * t) : (x1Coll + afterV1 * (t - collisionAt));
            double currentX2 = (!willCollide || t <= collisionAt) ? (x2_0 + v2 * t) : (x2Coll + afterV2 * (t - collisionAt));
            double currentV1 = (!willCollide || t < collisionAt) ? v1 : afterV1;
            double currentV2 = (!willCollide || t < collisionAt) ? v2 : afterV2;
            x1.add(currentX1);
            x2.add(currentX2);
            v1Series.add(currentV1);
            v2Series.add(currentV2);
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x1", x1);
        values.put("x2", x2);
        values.put("v1", v1Series);
        values.put("v2", v2Series);
        values.put("collisionTime", List.of(willCollide ? collisionAt : -1.0));
        values.put("collisionX", List.of(willCollide ? x1Coll : -1.0));

        return new SolverOutput(times,
                Map.of("x1", x1, "x2", x2),
                Map.of("v1", v1Series, "v2", v2Series),
                Map.of(),
                values);
    }

    private SolverOutput solveOscillation(JsonNode specification, Map<String, Double> overrides,
                                          double duration, double step) {
        double amplitude = Math.abs(PhysicsValues.require(specification, overrides, "amplitude", "a"));
        double mass = positive(PhysicsValues.require(specification, overrides, "mass", "m"));
        double spring = positive(PhysicsValues.require(specification, overrides, "spring_constant", "k"));
        double phase = PhysicsValues.require(specification, overrides, "phase", "phi");
        double omega = Math.sqrt(spring / mass);
        List<Double> times = new ArrayList<>();
        List<Double> position = new ArrayList<>();
        List<Double> velocity = new ArrayList<>();
        List<Double> acceleration = new ArrayList<>();
        int points = Math.max(1, (int) Math.ceil(Math.max(0.01, duration) / Math.max(0.001, step)));
        for (int i = 0; i <= points; i++) {
            double t = Math.min(Math.max(0.01, duration), i * Math.max(0.001, step));
            double angle = omega * t + phase;
            double x = amplitude * Math.cos(angle);
            double v = -amplitude * omega * Math.sin(angle);
            times.add(t);
            position.add(x);
            velocity.add(v);
            acceleration.add(-omega * omega * x);
        }
        return new SolverOutput(times, Map.of("x", position), Map.of("x", velocity),
                Map.of("x", acceleration), Map.of("x", position, "vx", velocity, "ax", acceleration));
    }

    private double calculateCollisionTime(double x1, double x2, double v1, double v2,
                                          Map<String, Double> overrides, JsonNode spec) {
        if (overrides != null && overrides.containsKey("collision_at")) return overrides.get("collision_at");
        if (overrides != null && overrides.containsKey("collision_time")) return overrides.get("collision_time");
        if (spec != null && spec.has("collision_time") && spec.get("collision_time").isNumber()) {
            return spec.get("collision_time").asDouble();
        }
        if (x1 < x2 && v1 > v2) {
            return (x2 - x1) / (v1 - v2);
        } else if (x1 > x2 && v2 > v1) {
            return (x1 - x2) / (v2 - v1);
        }
        return -1.0;
    }

    private static double positive(double value) {
        if (value <= 0) throw new IllegalArgumentException("Positive quantity required");
        return value;
    }
}
