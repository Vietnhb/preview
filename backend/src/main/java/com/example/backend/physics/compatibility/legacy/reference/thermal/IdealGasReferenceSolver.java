package com.example.backend.physics.compatibility.legacy.reference.thermal;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.model.thermal.IdealGasParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent reference evaluator for the isothermal ideal-gas process. */
@Component
public class IdealGasReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "ideal_gas_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"ideal_gas_isothermal".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported ideal-gas reference model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("timeSeconds must be finite");
        IdealGasParameters parameters = IdealGasParameters.from(specification, overrides);
        double volume = parameters.initialVolume() + parameters.volumeRate() * Math.max(0, timeSeconds);
        if (volume <= 0) throw new IllegalArgumentException("Ideal-gas volume reaches zero at the reference time");
        double pressure = parameters.pressureScale() / volume;
        return new AnalyticalPoint(Map.of("volume", volume, "pressure", pressure,
                "temperature", parameters.temperature(),
                "work", parameters.pressureScale() * Math.log(volume / parameters.initialVolume())));
    }
}
