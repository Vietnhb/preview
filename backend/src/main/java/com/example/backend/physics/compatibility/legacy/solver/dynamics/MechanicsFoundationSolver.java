package com.example.backend.physics.compatibility.legacy.solver.dynamics;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.dynamics.CircularMotionParameters;
import com.example.backend.physics.compatibility.legacy.model.dynamics.GravityOrbitParameters;
import com.example.backend.physics.model.dynamics.HookeLawParameters;
import com.example.backend.physics.model.dynamics.HydrostaticsParameters;
import com.example.backend.physics.compatibility.legacy.model.dynamics.WorkEnergyParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Analytical Grade 10 mechanics families not reducible to the kinematics solver. */
@Component
public class MechanicsFoundationSolver implements PhysicsSolver {
    @Override public String solverId() { return "mechanics_foundation_solver"; }

    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        var time = SimulationTimeline.sample(durationSeconds, stepSeconds);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        switch (model) {
            case "work_energy_power" -> {
                WorkEnergyParameters p = WorkEnergyParameters.from(specification, overrides);
                put(values, time, "workByForce", p.workByForce());
                put(values, time, "initialKineticEnergy", p.initialKineticEnergy());
                put(values, time, "finalKineticEnergy", p.finalKineticEnergy());
                put(values, time, "deltaKineticEnergy", p.deltaKineticEnergy());
                put(values, time, "averagePower", p.averagePower());
            }
            case "circular_motion" -> {
                CircularMotionParameters p = CircularMotionParameters.from(specification, overrides);
                put(values, time, "angularSpeed", p.angularSpeed());
                put(values, time, "period", p.period());
                put(values, time, "frequency", p.frequency());
                put(values, time, "centripetalAcceleration", p.centripetalAcceleration());
                put(values, time, "centripetalForce", p.centripetalForce());
            }
            case "hooke_law" -> {
                HookeLawParameters p = HookeLawParameters.from(PhysicsValues.bag(specification, overrides));
                put(values, time, "restoringForce", p.restoringForce());
                put(values, time, "elasticPotentialEnergy", p.elasticPotentialEnergy());
            }
            case "gravity_orbit" -> {
                GravityOrbitParameters p = GravityOrbitParameters.from(specification, overrides);
                put(values, time, "gravitationalForce", p.gravitationalForce());
                put(values, time, "gravitationalField", p.gravitationalField());
                put(values, time, "orbitalSpeed", p.orbitalSpeed());
                put(values, time, "orbitalPeriod", p.orbitalPeriod());
            }
            case "hydrostatics" -> {
                HydrostaticsParameters p = HydrostaticsParameters.from(PhysicsValues.bag(specification, overrides));
                put(values, time, "gaugePressure", p.gaugePressure());
                put(values, time, "absolutePressure", p.absolutePressure());
                put(values, time, "buoyantForce", p.buoyantForce());
            }
            default -> throw new IllegalArgumentException("Unsupported mechanics model: " + model);
        }
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    private static void put(Map<String, List<Double>> values, List<Double> time, String key, double value) {
        values.put(key, java.util.Collections.nCopies(time.size(), value));
    }
}
