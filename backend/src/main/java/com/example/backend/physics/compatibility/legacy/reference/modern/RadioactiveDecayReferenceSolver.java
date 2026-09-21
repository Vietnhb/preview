package com.example.backend.physics.compatibility.legacy.reference.modern;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.modern.RadioactiveDecayParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent exponential-decay reference evaluator. */
@Component
public class RadioactiveDecayReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "radioactive_decay_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"radioactive_decay".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported radioactive-decay reference model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("timeSeconds must be finite");
        RadioactiveDecayParameters parameters = RadioactiveDecayParameters.from(specification, overrides);
        double count = parameters.initialCount() * Math.exp(-parameters.decayConstant() * Math.max(0, timeSeconds));
        return new AnalyticalPoint(Map.of("remainingCount", count, "activity", parameters.decayConstant() * count));
    }
}
