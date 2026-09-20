package com.example.backend.physics.solver.modern;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.modern.EclipseGeometryParameters;
import com.example.backend.physics.runtime.SimulationTimeline;
import com.example.backend.physics.solver.TopicPhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Modern-physics application family. */
@Component
public class ModernApplicationsSolver implements TopicPhysicsSolver {
    @Override public String solverId() { return "modern_applications_solver"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("eclipse_geometry"); }

    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
            double duration, double step) {
        if (!"eclipse_geometry".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported modern application model: " + PhysicsValues.model(specification));
        }
        EclipseGeometryParameters p = EclipseGeometryParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        put(values, time, "starAngularDiameter", p.starAngularDiameter()); put(values, time, "occluderAngularDiameter", p.occluderAngularDiameter());
        put(values, time, "alignmentMargin", p.alignmentMargin()); put(values, time, "totality", p.totality());
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
    private static void put(Map<String, List<Double>> values, List<Double> time, String key, double value) {
        values.put(key, Collections.nCopies(time.size(), value));
    }
}
