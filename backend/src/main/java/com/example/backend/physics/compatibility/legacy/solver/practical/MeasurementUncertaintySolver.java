package com.example.backend.physics.compatibility.legacy.solver.practical;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.practical.MeasurementUncertaintyParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MeasurementUncertaintySolver implements PhysicsSolver {
    @Override public String solverId() { return "measurement_uncertainty_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double durationSeconds, double stepSeconds) {
        if (!"measurement_uncertainty".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported measurement model: " + PhysicsValues.model(specification));
        MeasurementUncertaintyParameters p = MeasurementUncertaintyParameters.from(specification, overrides);
        int points = Math.clamp((int) Math.ceil(Math.max(.01, durationSeconds) / Math.max(.001, stepSeconds)),
                1, 16_384);
        List<Double> time = new ArrayList<>(points + 1);
        List<Double> value = new ArrayList<>(points + 1);
        List<Double> uncertainty = new ArrayList<>(points + 1);
        List<Double> relative = new ArrayList<>(points + 1);
        List<Double> defined = new ArrayList<>(points + 1);
        List<Double> lower = new ArrayList<>(points + 1);
        List<Double> upper = new ArrayList<>(points + 1);
        for(int i=0;i<=points;i++){time.add(Math.clamp(i*Math.max(.001,stepSeconds), 0, Math.max(.01,durationSeconds)));value.add(p.measuredValue());uncertainty.add(p.absoluteUncertainty());relative.add(p.relativeUncertainty());defined.add(p.relativeUncertaintyDefined() ? 1.0 : 0.0);lower.add(p.lowerBound());upper.add(p.upperBound());}
        Map<String,List<Double>> values = new LinkedHashMap<>(); values.put("measuredValue",value); values.put("absoluteUncertainty",uncertainty); values.put("relativeUncertainty",relative); values.put("relativeUncertaintyDefined",defined); values.put("lowerBound",lower); values.put("upperBound",upper);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
