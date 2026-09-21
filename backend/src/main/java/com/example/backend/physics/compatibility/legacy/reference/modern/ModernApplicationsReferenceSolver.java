package com.example.backend.physics.compatibility.legacy.reference.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.modern.EclipseGeometryParameters;
import com.example.backend.physics.compatibility.legacy.reference.TopicReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Modern-physics application reference oracle. */
@Component
public class ModernApplicationsReferenceSolver implements TopicReferenceSolver {
    @Override public String solverId() { return "modern_applications_reference"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("eclipse_geometry"); }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double time) {
        if (!"eclipse_geometry".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported modern application model: " + PhysicsValues.model(specification));
        }
        EclipseGeometryParameters p = EclipseGeometryParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("starAngularDiameter", p.starAngularDiameter(),
                "occluderAngularDiameter", p.occluderAngularDiameter(), "alignmentMargin", p.alignmentMargin(),
                "totality", p.totality()));
    }
}
