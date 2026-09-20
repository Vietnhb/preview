package com.example.backend.physics.solver.practical;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.modern.EnergyEnvironmentParameters;
import com.example.backend.physics.runtime.SimulationTimeline;
import com.example.backend.physics.solver.TopicPhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Practical/data application family. */
@Component
public class PracticalApplicationsSolver implements TopicPhysicsSolver {
    @Override public String solverId() { return "practical_applications_solver"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("energy_environment"); }

    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
            double duration, double step) {
        if (!"energy_environment".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported practical application model: " + PhysicsValues.model(specification));
        }
        EnergyEnvironmentParameters p = EnergyEnvironmentParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        put(values, time, "renewableEnergy", p.renewableEnergy()); put(values, time, "fossilEnergy", p.fossilEnergy());
        put(values, time, "emissions", p.emissions()); put(values, time, "usefulEnergy", p.usefulEnergy());
        put(values, time, "avoidedEmissionsVsFossil", p.avoidedEmissionsVsFossil());
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
    private static void put(Map<String, List<Double>> values, List<Double> time, String key, double value) {
        values.put(key, Collections.nCopies(time.size(), value));
    }
}
