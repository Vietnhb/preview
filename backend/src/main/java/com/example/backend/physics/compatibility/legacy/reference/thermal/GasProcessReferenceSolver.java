package com.example.backend.physics.compatibility.legacy.reference.thermal;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.compatibility.legacy.model.thermal.GasProcessParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.AnalyticalPoint;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independent closed-form reference for ideal-gas path variants. */
@Component
public class GasProcessReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "gas_process_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        String model = PhysicsValues.model(specification);
        if (!model.equals("ideal_gas_isobaric") && !model.equals("ideal_gas_isochoric"))
            throw new IllegalArgumentException("Unsupported gas-process model: " + model);
        GasProcessParameters p = GasProcessParameters.from(specification, overrides);
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("initialPressure", p.initialPressure()); values.put("initialVolume", p.initialVolume());
        values.put("initialTemperature", p.initialTemperature()); values.put("finalTemperature", p.finalTemperature());
        if (model.equals("ideal_gas_isobaric")) {
            values.put("finalPressure", p.initialPressure()); values.put("finalVolume", p.isobaricFinalVolume()); values.put("work", p.isobaricWork());
        } else {
            values.put("finalPressure", p.isochoricFinalPressure()); values.put("finalVolume", p.initialVolume()); values.put("work", 0d);
        }
        return new AnalyticalPoint(values);
    }
}
