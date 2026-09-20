package com.example.backend.physics.reference.circuits;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.circuits.OhmsLawParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class OhmsLawReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "ohms_law_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"ohms_law".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported Ohm-law model: " + PhysicsValues.model(specification));
        OhmsLawParameters p = OhmsLawParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("voltage", p.voltage(), "resistance", p.resistance(), "current", p.current(), "power", p.power()));
    }
}
