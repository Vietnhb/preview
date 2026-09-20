package com.example.backend.physics.reference.optics;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.optics.ThinLensParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent reference evaluator for thin-lens imaging. */
@Component
public class ThinLensReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "thin_lens_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"thin_lens_imaging".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported thin-lens reference model: " + PhysicsValues.model(specification));
        }
        ThinLensParameters parameters = ThinLensParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("imageDistance", parameters.imageDistance(),
                "imageHeight", parameters.imageHeight(), "magnification", parameters.magnification()));
    }
}
