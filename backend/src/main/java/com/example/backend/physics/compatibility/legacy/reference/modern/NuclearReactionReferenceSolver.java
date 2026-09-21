package com.example.backend.physics.compatibility.legacy.reference.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.modern.NuclearReactionParameters;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NuclearReactionReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "nuclear_reaction_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"nuclear_reaction_energy".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported nuclear reaction model: " + PhysicsValues.model(specification));
        }
        NuclearReactionParameters p = NuclearReactionParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("massDefect", p.massDefect(),
                "releasedEnergyPerReaction", p.releasedEnergyPerReaction(),
                "totalReleasedEnergy", p.totalReleasedEnergy()));
    }
}
