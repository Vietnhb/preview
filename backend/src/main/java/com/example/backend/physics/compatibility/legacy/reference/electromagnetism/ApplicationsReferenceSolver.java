package com.example.backend.physics.compatibility.legacy.reference.electromagnetism;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.example.backend.physics.compatibility.legacy.reference.TopicReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compatibility adapter for the historical multi-topic reference binding. */
@Component
public class ApplicationsReferenceSolver implements ReferenceSolver {
    private final Map<String, ReferenceSolver> routes;

    public ApplicationsReferenceSolver() {
        this(List.of(new com.example.backend.physics.compatibility.legacy.reference.circuits.CircuitApplicationsReferenceSolver(),
                new com.example.backend.physics.compatibility.legacy.reference.waves.WaveApplicationsReferenceSolver(),
                new com.example.backend.physics.compatibility.legacy.reference.modern.ModernApplicationsReferenceSolver(),
                new com.example.backend.physics.compatibility.legacy.reference.practical.PracticalApplicationsReferenceSolver()));
    }

    @Autowired
    public ApplicationsReferenceSolver(List<TopicReferenceSolver> solvers) {
        Map<String, ReferenceSolver> mapped = new LinkedHashMap<>();
        for (TopicReferenceSolver solver : solvers) {
            for (String model : solver.supportedModels()) {
                if (mapped.putIfAbsent(model, solver) != null) {
                    throw new IllegalStateException("Duplicate legacy application reference binding: " + model);
                }
            }
        }
        routes = Map.copyOf(mapped);
    }

    @Override public String solverId() { return "applications_reference"; }

    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double time) {
        String model = PhysicsValues.model(specification);
        ReferenceSolver delegate = routes.get(model);
        if (delegate == null) throw new IllegalArgumentException("Unsupported legacy application model: " + model);
        return delegate.solve(specification, overrides, time);
    }
}
