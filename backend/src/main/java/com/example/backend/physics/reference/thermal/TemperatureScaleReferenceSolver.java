package com.example.backend.physics.reference.thermal;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.thermal.TemperatureScaleParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class TemperatureScaleReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "temperature_scale_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"temperature_scales".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported temperature-scale model: " + PhysicsValues.model(specification));
        TemperatureScaleParameters p = TemperatureScaleParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("celsius", p.celsius(), "kelvin", p.kelvin(), "fahrenheit", p.fahrenheit()));
    }
}
