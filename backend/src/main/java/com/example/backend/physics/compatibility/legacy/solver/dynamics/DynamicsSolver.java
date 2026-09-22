package com.example.backend.physics.compatibility.legacy.solver.dynamics;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DynamicsSolver implements PhysicsSolver {
    private static final double VELOCITY_EPSILON = 1e-10;

    @Override
    public String solverId() { return "dynamics_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        if (model.equals("elastic_collision")) {
            if (hasEntityBodies(specification)) return solveEntityCollision(specification, overrides, durationSeconds, stepSeconds);
            return solveCollision(specification, overrides, durationSeconds, stepSeconds);
        }
        if (model.equals("spring")) {
            return solveOscillation(specification, overrides, durationSeconds, stepSeconds);
        }
        if (model.equals("forces")) return solveForces(specification, overrides, durationSeconds, stepSeconds);
        throw new IllegalArgumentException("Unsupported dynamics model: " + model);
    }

    private SolverOutput solveForces(JsonNode specification, Map<String, Double> overrides,
                                     double duration, double step) {
        double mass = positive(PhysicsValues.require(specification, overrides, "mass"));
        double force = PhysicsValues.require(specification, overrides, "net_force");
        double friction = nonNegative(PhysicsValues.require(specification, overrides, "friction_coefficient"));
        double gravity = PhysicsValues.gravitationalAcceleration(specification, overrides);
        double frictionForce = friction * mass * gravity;
        double velocity = PhysicsValues.require(specification, overrides, "initial_velocity");
        double position = PhysicsValues.require(specification, overrides, "initial_position");
        List<Double> times = new ArrayList<>();
        List<Double> x = new ArrayList<>();
        List<Double> vx = new ArrayList<>();
        List<Double> ax = new ArrayList<>();
        List<Double> netForce = new ArrayList<>();
        double effectiveDuration = Math.max(0.01, duration);
        double effectiveStep = Math.max(0.001, step);
        int points = Math.max(1, (int) Math.ceil(effectiveDuration / effectiveStep));
        for (int i = 0; i <= points; i++) {
            double t = Math.min(effectiveDuration, i * effectiveStep);
            double acceleration = acceleration(force, frictionForce, mass, velocity);
            times.add(t);
            x.add(position);
            vx.add(velocity);
            ax.add(acceleration);
            netForce.add(mass * acceleration);
            if (i == points) break;
            double dt = Math.min(effectiveStep, effectiveDuration - t);
            MotionState next = advance(position, velocity, force, frictionForce, mass, dt);
            position = next.position();
            velocity = next.velocity();
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
        double m1 = positive(PhysicsValues.require(specification, overrides, "mass_1"));
        double m2 = positive(PhysicsValues.require(specification, overrides, "mass_2"));
        double x1Initial = PhysicsValues.require(specification, overrides, "initial_position_1");
        double x2Initial = PhysicsValues.require(specification, overrides, "initial_position_2");
        double v1 = PhysicsValues.require(specification, overrides, "velocity_1");
        double v2 = PhysicsValues.require(specification, overrides, "velocity_2");

        double collisionAt = calculateCollisionTime(x1Initial, x2Initial, v1, v2);
        boolean willCollide = collisionAt > 0;
        double afterV1 = willCollide ? ((m1 - m2) * v1 + 2 * m2 * v2) / (m1 + m2) : v1;
        double afterV2 = willCollide ? ((m2 - m1) * v2 + 2 * m1 * v1) / (m1 + m2) : v2;
        double x1Coll = willCollide ? (x1Initial + v1 * collisionAt) : 0;
        double x2Coll = willCollide ? (x2Initial + v2 * collisionAt) : 0;

        List<Double> times = new ArrayList<>();
        List<Double> x1 = new ArrayList<>();
        List<Double> x2 = new ArrayList<>();
        List<Double> v1Series = new ArrayList<>();
        List<Double> v2Series = new ArrayList<>();
        double effectiveDuration = Math.max(0.01, duration);
        double effectiveStep = Math.max(0.001, step);
        int points = Math.max(1, (int) Math.ceil(effectiveDuration / effectiveStep));
        for (int i = 0; i <= points; i++) {
            double t = Math.min(effectiveDuration, i * effectiveStep);
            times.add(t);
            double currentX1 = positionAt(x1Initial, v1, x1Coll, afterV1, t, collisionAt, willCollide);
            double currentX2 = positionAt(x2Initial, v2, x2Coll, afterV2, t, collisionAt, willCollide);
            double currentV1 = velocityAt(v1, afterV1, t, collisionAt, willCollide);
            double currentV2 = velocityAt(v2, afterV2, t, collisionAt, willCollide);
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

    /** Event-driven 1-D elastic collisions for the actual entity list in the specification. */
    private SolverOutput solveEntityCollision(JsonNode specification, Map<String, Double> overrides,
            double duration, double step) {
        List<EntityBody> initial = entityBodies(specification, overrides);
        if (initial.size() < 2) throw new IllegalArgumentException("At least two collision entities are required");
        double effectiveDuration = Math.max(0.01, duration);
        double effectiveStep = Math.max(0.001, step);
        int points = Math.max(1, (int) Math.ceil(effectiveDuration / effectiveStep));
        List<Double> times = new ArrayList<>();
        Map<String, List<Double>> positions = new LinkedHashMap<>();
        Map<String, List<Double>> velocities = new LinkedHashMap<>();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        initial.forEach(body -> {
            positions.put(body.id(), new ArrayList<>());
            velocities.put(body.id(), new ArrayList<>());
            values.put(body.id(), new ArrayList<>());
            values.put("velocity." + body.id(), new ArrayList<>());
        });
        for (int index = 0; index <= points; index++) {
            double time = Math.min(effectiveDuration, index * effectiveStep);
            List<EntityBody> state = stateAt(initial, time);
            times.add(time);
            for (EntityBody body : state) {
                positions.get(body.id()).add(body.position());
                velocities.get(body.id()).add(body.velocity());
                values.get(body.id()).add(body.position());
                values.get("velocity." + body.id()).add(body.velocity());
            }
        }
        return new SolverOutput(times, positions, velocities, Map.of(), values);
    }

    private List<EntityBody> entityBodies(JsonNode specification, Map<String, Double> overrides) {
        List<EntityBody> result = new ArrayList<>();
        for (JsonNode object : specification.path("objects")) {
            String id = object.path("id").asText("").trim();
            if (id.isBlank()) throw new IllegalArgumentException("Collision entity id is required");
            double mass = positive(entityQuantity(object, overrides, "mass", "m"));
            double position = entityQuantity(object, overrides, "initial_position", "position", "x");
            double velocity = entityQuantity(object, overrides, "initial_velocity", "velocity", "v");
            result.add(new EntityBody(id, mass, position, velocity));
        }
        return List.copyOf(result);
    }

    private double entityQuantity(JsonNode object, Map<String, Double> overrides, String... names) {
        String id = object.path("id").asText("");
        for (String name : names) {
            for (String overrideKey : List.of("objects." + id + "." + name, id + "." + name,
                    id + ":" + name)) {
                if (overrides != null && overrides.containsKey(overrideKey)) return overrides.get(overrideKey);
            }
            for (JsonNode quantity : object.path("quantities")) {
                if (!name.equals(quantity.path("name").asText(""))) continue;
                JsonNode value = quantity.has("normalizedValue") ? quantity.get("normalizedValue") : quantity.get("value");
                if (value != null && value.isNumber() && Double.isFinite(value.asDouble())) return value.asDouble();
            }
        }
        throw new IllegalArgumentException("Missing entity quantity for " + id + ": " + names[0]);
    }

    private List<EntityBody> stateAt(List<EntityBody> initial, double targetTime) {
        List<EntityBody> state = new ArrayList<>(initial);
        double elapsed = 0;
        while (elapsed < targetTime - 1e-10) {
            CollisionEvent event = nextCollision(state);
            double remaining = targetTime - elapsed;
            if (event == null || event.afterSeconds() > remaining + 1e-10) {
                advance(state, remaining);
                break;
            }
            if (event.afterSeconds() <= 1e-10) {
                throw new IllegalArgumentException("Simultaneous or overlapping collision is unsupported");
            }
            advance(state, event.afterSeconds());
            EntityBody first = state.get(event.firstIndex());
            EntityBody second = state.get(event.secondIndex());
            double firstVelocity = ((first.mass() - second.mass()) * first.velocity()
                    + 2 * second.mass() * second.velocity()) / (first.mass() + second.mass());
            double secondVelocity = ((second.mass() - first.mass()) * second.velocity()
                    + 2 * first.mass() * first.velocity()) / (first.mass() + second.mass());
            state.set(event.firstIndex(), first.withVelocity(firstVelocity));
            state.set(event.secondIndex(), second.withVelocity(secondVelocity));
            elapsed += event.afterSeconds();
        }
        return state;
    }

    private CollisionEvent nextCollision(List<EntityBody> state) {
        CollisionEvent best = null;
        for (int i = 0; i < state.size(); i++) {
            for (int j = i + 1; j < state.size(); j++) {
                EntityBody first = state.get(i);
                EntityBody second = state.get(j);
                double after = calculateCollisionTime(first.position(), second.position(), first.velocity(), second.velocity());
                if (after <= 0 || best != null && after > best.afterSeconds() + 1e-9) continue;
                if (best != null && Math.abs(after - best.afterSeconds()) <= 1e-9) {
                    throw new IllegalArgumentException("Simultaneous collision is unsupported without a declared contact model");
                }
                best = new CollisionEvent(i, j, after);
            }
        }
        return best;
    }

    private void advance(List<EntityBody> state, double seconds) {
        for (int i = 0; i < state.size(); i++) {
            EntityBody body = state.get(i);
            state.set(i, body.withPosition(body.position() + body.velocity() * seconds));
        }
    }

    private boolean hasEntityBodies(JsonNode specification) {
        JsonNode objects = specification.path("objects");
        if (!objects.isArray() || objects.size() < 2) return false;
        if (objects.size() > 512) throw new IllegalArgumentException("Collision entity count exceeds 512");
        for (JsonNode object : objects) if (!object.path("quantities").isArray() || object.path("quantities").isEmpty()) return false;
        return true;
    }

    private record EntityBody(String id, double mass, double position, double velocity) {
        EntityBody withPosition(double value) { return new EntityBody(id, mass, value, velocity); }
        EntityBody withVelocity(double value) { return new EntityBody(id, mass, position, value); }
    }

    private record CollisionEvent(int firstIndex, int secondIndex, double afterSeconds) { }

    private SolverOutput solveOscillation(JsonNode specification, Map<String, Double> overrides,
                                          double duration, double step) {
        double amplitude = Math.abs(PhysicsValues.require(specification, overrides, "amplitude"));
        double mass = positive(PhysicsValues.require(specification, overrides, "mass"));
        double spring = positive(PhysicsValues.require(specification, overrides, "spring_constant"));
        double phase = PhysicsValues.require(specification, overrides, "phase");
        double omega = Math.sqrt(spring / mass);
        List<Double> times = new ArrayList<>();
        List<Double> position = new ArrayList<>();
        List<Double> velocity = new ArrayList<>();
        List<Double> acceleration = new ArrayList<>();
        double effectiveDuration = Math.max(0.01, duration);
        double effectiveStep = Math.max(0.001, step);
        int points = Math.max(1, (int) Math.ceil(effectiveDuration / effectiveStep));
        for (int i = 0; i <= points; i++) {
            double t = Math.min(effectiveDuration, i * effectiveStep);
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

    private double calculateCollisionTime(double x1, double x2, double v1, double v2) {
        if (x1 < x2 && v1 > v2) {
            return (x2 - x1) / (v1 - v2);
        } else if (x1 > x2 && v2 > v1) {
            return (x1 - x2) / (v2 - v1);
        }
        return -1.0;
    }

    private static double positionAt(double initialPosition, double initialVelocity,
                                     double collisionPosition, double finalVelocity,
                                     double time, double collisionTime, boolean willCollide) {
        return !willCollide || time <= collisionTime
                ? initialPosition + initialVelocity * time
                : collisionPosition + finalVelocity * (time - collisionTime);
    }

    private static double velocityAt(double initialVelocity, double finalVelocity,
                                     double time, double collisionTime, boolean willCollide) {
        return !willCollide || time < collisionTime ? initialVelocity : finalVelocity;
    }

    private static double positive(double value) {
        if (value <= 0) throw new IllegalArgumentException("Positive quantity required");
        return value;
    }

    private static double nonNegative(double value) {
        if (value < 0) throw new IllegalArgumentException("Non-negative quantity required");
        return value;
    }

    private static double acceleration(double appliedForce, double frictionForce, double mass, double velocity) {
        if (Math.abs(velocity) <= VELOCITY_EPSILON) {
            if (Math.abs(appliedForce) <= frictionForce) return 0;
            return Math.copySign((Math.abs(appliedForce) - frictionForce) / mass, appliedForce);
        }
        return (appliedForce - Math.copySign(frictionForce, velocity)) / mass;
    }

    private static MotionState advance(double position, double velocity, double appliedForce,
                                       double frictionForce, double mass, double duration) {
        double acceleration = acceleration(appliedForce, frictionForce, mass, velocity);
        if (Math.abs(velocity) > VELOCITY_EPSILON && velocity * acceleration < 0) {
            double stoppingTime = -velocity / acceleration;
            if (stoppingTime <= duration) {
                double stoppedPosition = position + velocity * stoppingTime
                        + 0.5 * acceleration * stoppingTime * stoppingTime;
                double remaining = duration - stoppingTime;
                double restartAcceleration = acceleration(appliedForce, frictionForce, mass, 0);
                return new MotionState(stoppedPosition + 0.5 * restartAcceleration * remaining * remaining,
                        restartAcceleration * remaining);
            }
        }
        return new MotionState(position + velocity * duration + 0.5 * acceleration * duration * duration,
                velocity + acceleration * duration);
    }

    private record MotionState(double position, double velocity) { }
}
