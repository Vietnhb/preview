package com.example.backend.physics.compatibility.legacy.reference.circuits;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.circuits.AcWaveformParameters;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AcWaveformReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "ac_waveform_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"ac_waveform".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported AC waveform model: " + PhysicsValues.model(specification));
        AcWaveformParameters p = AcWaveformParameters.from(PhysicsValues.bag(specification, overrides));
        return new AnalyticalPoint(Map.of("voltage", p.voltage(Math.max(0, timeSeconds)), "rmsVoltage", p.rmsVoltage()));
    }
}
