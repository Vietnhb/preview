package com.example.backend.physics.compatibility.legacy.reference.optics;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.model.optics.DiffractionParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class DiffractionReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "diffraction_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"diffraction_polarization".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported diffraction model: " + PhysicsValues.model(specification));
        DiffractionParameters p = DiffractionParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("diffractionAngle", p.diffractionAngle(), "minimumExists", p.minimumExists() ? 1.0 : 0.0, "transmittedIntensity", p.transmittedIntensity()));
    }
}
