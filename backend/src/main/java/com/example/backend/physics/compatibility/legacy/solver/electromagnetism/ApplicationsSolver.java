package com.example.backend.physics.compatibility.legacy.solver.electromagnetism;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.example.backend.physics.compatibility.legacy.solver.TopicPhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility adapter for persisted catalog bindings written as
 * {@code applications_solver}. New schema versions bind directly to the
 * topic-owned modules and never dispatch here.
 */
@Component
public class ApplicationsSolver implements PhysicsSolver {
    private final Map<String, PhysicsSolver> routes;

    public ApplicationsSolver() {
        this(List.of(new com.example.backend.physics.compatibility.legacy.solver.circuits.CircuitApplicationsSolver(),
                new com.example.backend.physics.compatibility.legacy.solver.waves.WaveApplicationsSolver(),
                new com.example.backend.physics.compatibility.legacy.solver.modern.ModernApplicationsSolver(),
                new com.example.backend.physics.compatibility.legacy.solver.practical.PracticalApplicationsSolver()));
    }

    @Autowired
    public ApplicationsSolver(List<TopicPhysicsSolver> solvers) {
        Map<String, PhysicsSolver> mapped = new LinkedHashMap<>();
        for (TopicPhysicsSolver solver : solvers) {
            for (String model : solver.supportedModels()) {
                if (mapped.putIfAbsent(model, solver) != null) {
                    throw new IllegalStateException("Duplicate legacy application model binding: " + model);
                }
            }
        }
        routes = Map.copyOf(mapped);
    }

    @Override public String solverId() { return "applications_solver"; }

    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
            double duration, double step) {
        String model = PhysicsValues.model(specification);
        PhysicsSolver delegate = routes.get(model);
        if (delegate == null) throw new IllegalArgumentException("Unsupported legacy application model: " + model);
        return delegate.solve(specification, overrides, duration, step);
    }
}
