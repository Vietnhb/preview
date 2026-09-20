package com.example.backend.physics.reference.electromagnetism;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.*;
import com.example.backend.physics.model.modern.*;
import com.example.backend.physics.model.electromagnetism.*;
import com.example.backend.physics.model.dynamics.*;
import com.example.backend.physics.model.optics.*;
import com.example.backend.physics.model.thermal.*;
import com.example.backend.physics.model.waves.*;
import com.example.backend.physics.model.practical.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PointChargeFieldReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "electric_field_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"point_charge_field".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported electric-field model: " + PhysicsValues.model(specification));
        PointChargeFieldParameters p = PointChargeFieldParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("electricField", p.electricFieldMagnitude(), "electricPotential", p.electricPotential()));
    }
}
