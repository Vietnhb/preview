package com.example.backend.physics.reference.thermal;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.thermal.CalorimetryParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class CalorimetryReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "calorimetry_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"calorimetry_mixing".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported calorimetry model: " + PhysicsValues.model(specification));
        CalorimetryParameters p = CalorimetryParameters.from(specification, overrides);
        double te = p.equilibriumTemperature();
        double t1 = te;
        double t2 = te;
        return new AnalyticalPoint(Map.of("temperature1", t1, "temperature2", t2, "equilibriumTemperature", te,
                "heat1", p.mass1() * p.specificHeat1() * (t1 - p.initialTemperature1()),
                "heat2", p.mass2() * p.specificHeat2() * (t2 - p.initialTemperature2())));
    }
}
