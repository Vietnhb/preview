package com.example.backend.physics.compatibility.legacy.reference.modern;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.model.modern.HydrogenSpectrumParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class HydrogenSpectrumReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "hydrogen_spectrum_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"atomic_spectra".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported atomic-spectrum model: " + PhysicsValues.model(specification));
        HydrogenSpectrumParameters p = HydrogenSpectrumParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("wavelength", p.wavelength(), "frequency", p.frequency(), "photonEnergy", p.photonEnergy()));
    }
}
