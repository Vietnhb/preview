package com.example.backend.physics.solver.modern;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.modern.EnergyBandTransitionParameters;
import com.example.backend.physics.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EnergyBandSolver implements PhysicsSolver {
    @Override public String solverId() { return "energy_band_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"energy_band_transition".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported energy-band model: " + PhysicsValues.model(specification));
        }
        EnergyBandTransitionParameters p = EnergyBandTransitionParameters.from(specification, overrides);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("bandGap", Collections.singletonList(p.bandGap()));
        values.put("photonEnergy", Collections.singletonList(p.photonEnergy()));
        values.put("thresholdWavelength", Collections.singletonList(p.thresholdWavelength()));
        values.put("transitionAllowed", Collections.singletonList(p.transitionAllowed()));
        return new SolverOutput(List.of(0d), Map.of(), Map.of(), Map.of(), values);
    }
}
