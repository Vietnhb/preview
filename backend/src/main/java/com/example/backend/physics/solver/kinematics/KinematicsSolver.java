package com.example.backend.physics.solver.kinematics;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.kinematics.KinematicsParameters;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KinematicsSolver implements PhysicsSolver {
    @Override
    public String solverId() { return "kinematics_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        KinematicsParameters parameters = KinematicsParameters.from(specification, overrides);
        boolean projectile = parameters.projectile();
        double duration = Math.max(0.01, durationSeconds);
        double step = Math.clamp(stepSeconds, 0.001, 0.2);
        double vx = projectile ? parameters.initialVelocity() * Math.cos(parameters.launchAngle()) : parameters.initialVelocity();
        double vy = projectile ? parameters.initialVelocity() * Math.sin(parameters.launchAngle()) : 0;
        return simulate(new SimulationState(projectile, duration, step, parameters.initialPosition(),
                parameters.acceleration(), parameters.initialHeight(), vx, vy, parameters.gravity()));
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
        double gravity = state.gravity();
        List<Double> time = new ArrayList<>();
        Map<String, List<Double>> positions = new LinkedHashMap<>();
        Map<String, List<Double>> velocities = new LinkedHashMap<>();
        Map<String, List<Double>> accelerations = new LinkedHashMap<>();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> xSeries = new ArrayList<>();
        List<Double> displacementSeries = new ArrayList<>();
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
            displacementSeries.add(x - state.x());
            ySeries.add(projectile ? y : 0d);
            vxSeries.add(vx);
            vySeries.add(projectile ? vy : 0d);
            axSeries.add(projectile ? 0d : acceleration);
            aySeries.add(projectile ? -gravity : 0d);
            if (i == points) break;
            double dt = Math.min(step, duration - currentTime);
            x += vx * dt;
            if (projectile) {
                y += vy * dt - 0.5 * gravity * dt * dt;
                vy -= gravity * dt;
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
        values.put("displacement", displacementSeries);
        values.put("y", ySeries);
        values.put("vx", vxSeries);
        values.put("vy", vySeries);
        values.put("ax", axSeries);
        values.put("ay", aySeries);
        return new SolverOutput(time, positions, velocities, accelerations, values);
    }

    private record SimulationState(boolean projectile, double duration, double step,
                                   double x, double acceleration, double y, double vx, double vy,
                                   double gravity) { }

}
