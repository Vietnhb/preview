package com.example.backend.physics.compatibility.legacy.reference.dynamics;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class DynamicsReferenceSolver implements ReferenceSolver {
    private static final double VELOCITY_EPSILON = 1e-10;
    public String solverId() { return "dynamics_reference"; }
    public AnalyticalPoint solve(JsonNode spec, Map<String, Double> overrides, double seconds) {
        String model = PhysicsValues.model(spec);
        if (model.equals("elastic_collision")) {
            if (hasEntityBodies(spec)) return entityCollision(spec, overrides, seconds);
            return collision(spec, overrides, seconds);
        }
        if (model.equals("spring")) return spring(spec, overrides, seconds);
        if (!model.equals("forces")) throw new IllegalArgumentException("Unsupported dynamics reference model: " + model);
        double mass = positive(PhysicsValues.require(spec, overrides, "mass"));
        double force = PhysicsValues.require(spec, overrides, "net_force");
        double friction = nonNegative(PhysicsValues.require(spec, overrides, "friction_coefficient"));
        double gravity = PhysicsValues.gravitationalAcceleration(spec, overrides);
        double frictionForce = friction * mass * gravity;
        double v0 = PhysicsValues.require(spec, overrides, "initial_velocity");
        double x0 = PhysicsValues.require(spec, overrides, "initial_position");
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
        double m1 = positive(PhysicsValues.require(spec, overrides, "mass_1"));
        double m2 = positive(PhysicsValues.require(spec, overrides, "mass_2"));
        double x1 = PhysicsValues.require(spec, overrides, "initial_position_1");
        double x2 = PhysicsValues.require(spec, overrides, "initial_position_2");
        double v1 = PhysicsValues.require(spec, overrides, "velocity_1");
        double v2 = PhysicsValues.require(spec, overrides, "velocity_2");
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

    private AnalyticalPoint entityCollision(JsonNode spec, Map<String, Double> overrides, double seconds) {
        List<EntityBody> state = new ArrayList<>();
        for (JsonNode object : spec.path("objects")) {
            String id = object.path("id").asText("").trim();
            if (id.isBlank()) throw new IllegalArgumentException("Collision entity id is required");
            state.add(new EntityBody(id, positive(entityQuantity(object, overrides, "mass", "m")),
                    entityQuantity(object, overrides, "initial_position", "position", "x"),
                    entityQuantity(object, overrides, "initial_velocity", "velocity", "v")));
        }
        if (state.size() < 2) throw new IllegalArgumentException("At least two collision entities are required");
        double target = Math.max(0, seconds);
        double elapsed = 0;
        while (elapsed < target - 1e-10) {
            CollisionEvent event = nextCollision(state);
            double remaining = target - elapsed;
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
        Map<String, Double> values = new java.util.LinkedHashMap<>();
        for (EntityBody body : state) {
            values.put(body.id(), body.position());
            values.put("velocity." + body.id(), body.velocity());
        }
        return new AnalyticalPoint(values);
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

    private CollisionEvent nextCollision(List<EntityBody> state) {
        CollisionEvent best = null;
        for (int i = 0; i < state.size(); i++) {
            for (int j = i + 1; j < state.size(); j++) {
                EntityBody first = state.get(i);
                EntityBody second = state.get(j);
                double after = collisionTime(first.position(), second.position(), first.velocity(), second.velocity());
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
    private AnalyticalPoint spring(JsonNode spec, Map<String, Double> overrides, double seconds) {
        double amplitude = positive(PhysicsValues.require(spec, overrides, "amplitude"));
        double mass = positive(PhysicsValues.require(spec, overrides, "mass"));
        double stiffness = positive(PhysicsValues.require(spec, overrides, "spring_constant"));
        double phase = PhysicsValues.require(spec, overrides, "phase");
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
