package com.example.backend.physics.reference.optics;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.solver.optics.OpticalInstrumentSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent reference evaluator for paraxial optical instruments. */
@Component
public class OpticalInstrumentReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "optical_instrument_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        String model = PhysicsValues.model(specification);
        return new AnalyticalPoint(OpticalInstrumentSolver.evaluate(model, specification, overrides));
    }
}
