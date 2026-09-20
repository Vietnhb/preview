package com.example.backend.physics.reference.circuits;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.circuits.AcRlcParameters;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AcRlcReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "ac_rlc_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"ac_rlc_circuit".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported AC RLC model: " + PhysicsValues.model(specification));
        AcRlcParameters p = AcRlcParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("impedance", p.impedance(), "rmsCurrent", p.rmsCurrent(), "powerFactor", p.powerFactor(), "realPower", p.realPower(), "phase", p.phase()));
    }
}
