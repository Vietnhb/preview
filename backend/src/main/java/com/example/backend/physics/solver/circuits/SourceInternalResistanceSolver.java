package com.example.backend.physics.solver.circuits;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.circuits.SourceInternalResistanceParameters;
import com.example.backend.physics.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SourceInternalResistanceSolver implements PhysicsSolver {
    @Override public String solverId() { return "source_internal_resistance_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"source_internal_resistance".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported source model: " + PhysicsValues.model(specification));
        }
        SourceInternalResistanceParameters p = SourceInternalResistanceParameters.from(specification, overrides);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("current", Collections.singletonList(p.current()));
        values.put("terminalVoltage", Collections.singletonList(p.terminalVoltage()));
        values.put("loadPower", Collections.singletonList(p.loadPower()));
        values.put("internalPowerLoss", Collections.singletonList(p.internalPowerLoss()));
        values.put("efficiency", Collections.singletonList(p.efficiency()));
        return new SolverOutput(List.of(0d), Map.of(), Map.of(), Map.of(), values);
    }
}
