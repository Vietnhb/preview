package com.example.backend.physics;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KinematicsSolver implements PhysicsSolver {
    private static final double GRAVITY = 9.81;

    @Override
    public String solverId() { return "kinematics_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        if (!model.equals("uniform_acceleration_1d") && !model.equals("projectile_2d")) throw new IllegalArgumentException("Unsupported kinematics model: " + model);
        boolean projectile = model.equals("projectile_2d");
        double duration = Math.max(0.01, durationSeconds);
        double step = Math.clamp(stepSeconds, 0.001, 0.2);
        double x = PhysicsValues.require(specification, overrides, "initial_position", "x0", "position");
        double velocity = PhysicsValues.require(specification, overrides, "initial_velocity", "v0", "velocity");
        double acceleration = projectile ? 0 : PhysicsValues.require(specification, overrides, "acceleration", "a");
        double y = projectile ? PhysicsValues.require(specification, overrides, "initial_height", "y0", "height") : 0;
        double angle = projectile ? PhysicsValues.require(specification, overrides, "launch_angle", "angle", "theta") : 0;
        double vx = projectile ? velocity * Math.cos(angle) : velocity;
        double vy = projectile ? velocity * Math.sin(angle) : 0;
        return simulate(new SimulationState(projectile, duration, step, x, acceleration, y, vx, vy));
    }

    private SolverOutput simulate(SimulationState state) {
        boolean projectile = state.projectile();
        double duration = state.duration();
        double step = state.step();
        double x = state.x();
        double acceleration = state.acceleration();
        double y = state.y();
        double vx = state.vx();
        double vy = state.vy();
        List<Double> time = new ArrayList<>();
        Map<String, List<Double>> positions = new LinkedHashMap<>();
        Map<String, List<Double>> velocities = new LinkedHashMap<>();
        Map<String, List<Double>> accelerations = new LinkedHashMap<>();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> xSeries = new ArrayList<>();
        List<Double> ySeries = new ArrayList<>();
        List<Double> vxSeries = new ArrayList<>();
        List<Double> vySeries = new ArrayList<>();
        List<Double> axSeries = new ArrayList<>();
        List<Double> aySeries = new ArrayList<>();

        int points = Math.max(1, (int) Math.ceil(duration / step));
        for (int i = 0; i <= points; i++) {
            double currentTime = Math.min(duration, i * step);
            time.add(currentTime);
            xSeries.add(x);
            ySeries.add(projectile ? y : 0d);
            vxSeries.add(vx);
            vySeries.add(projectile ? vy : 0d);
            axSeries.add(projectile ? 0d : acceleration);
            aySeries.add(projectile ? -GRAVITY : 0d);
            if (i == points) break;
            double dt = Math.min(step, duration - currentTime);
            x += vx * dt;
            if (projectile) {
                y += vy * dt - 0.5 * GRAVITY * dt * dt;
                vy -= GRAVITY * dt;
            } else {
                x += 0.5 * acceleration * dt * dt;
                vx += acceleration * dt;
            }
        }
        positions.put("x", xSeries);
        positions.put("y", ySeries);
        velocities.put("x", vxSeries);
        velocities.put("y", vySeries);
        accelerations.put("x", axSeries);
        accelerations.put("y", aySeries);
        values.put("x", xSeries);
        values.put("y", ySeries);
        values.put("vx", vxSeries);
        values.put("vy", vySeries);
        values.put("ax", axSeries);
        values.put("ay", aySeries);
        return new SolverOutput(time, positions, velocities, accelerations, values);
    }

    private record SimulationState(boolean projectile, double duration, double step,
                                   double x, double acceleration, double y, double vx, double vy) { }

}
