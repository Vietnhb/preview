package com.example.backend.physics.reference.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.modern.EnergyBandTransitionParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EnergyBandReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "energy_band_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"energy_band_transition".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported energy-band model: " + PhysicsValues.model(specification));
        }
        EnergyBandTransitionParameters p = EnergyBandTransitionParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("bandGap", p.bandGap(), "photonEnergy", p.photonEnergy(),
                "thresholdWavelength", p.thresholdWavelength(), "transitionAllowed", p.transitionAllowed()));
    }
}
