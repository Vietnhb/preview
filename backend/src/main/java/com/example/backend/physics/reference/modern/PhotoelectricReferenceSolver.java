package com.example.backend.physics.reference.modern;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.modern.PhotoelectricParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PhotoelectricReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "photoelectric_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"photoelectric_effect".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported photoelectric model: " + PhysicsValues.model(specification));
        PhotoelectricParameters p = PhotoelectricParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("photonEnergy", p.photonEnergy(), "maximumKineticEnergy", p.maximumKineticEnergy(), "stoppingPotential", p.stoppingPotential(), "wavelength", p.wavelength(), "emissionOccurs", p.emissionOccurs() ? 1.0 : 0.0));
    }
}
