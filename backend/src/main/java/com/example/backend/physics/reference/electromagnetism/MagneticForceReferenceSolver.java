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
public class MagneticForceReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "magnetic_force_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"magnetic_force".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported magnetic-force model: " + PhysicsValues.model(specification));
        return new AnalyticalPoint(Map.of("magneticForce", MagneticForceParameters.from(specification, overrides).forceMagnitude()));
    }
}
