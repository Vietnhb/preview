package com.example.backend.physics.reference.circuits;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.circuits.ResistorNetworkParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ResistorNetworkReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "resistor_network_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        String model = PhysicsValues.model(specification);
        if (!model.equals("resistors_series") && !model.equals("resistors_parallel")) throw new IllegalArgumentException("Unsupported resistor-network model: " + model);
        ResistorNetworkParameters p = ResistorNetworkParameters.from(specification, overrides);
        boolean parallel = model.equals("resistors_parallel");
        double equivalent = parallel ? p.parallelResistance() : p.seriesResistance();
        double total = p.voltage() / equivalent;
        return new AnalyticalPoint(Map.of("equivalentResistance", equivalent, "totalCurrent", total,
                "branchCurrent1", parallel ? p.voltage() / p.resistance1() : total,
                "branchCurrent2", parallel ? p.voltage() / p.resistance2() : total));
    }
}
