package com.example.backend.physics.compatibility.legacy.model.thermal;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical inputs shared by reversible isobaric and isochoric ideal-gas paths. */
public record GasProcessParameters(double initialPressure, double initialVolume,
                                   double initialTemperature, double finalTemperature) {
    public static GasProcessParameters from(JsonNode specification, Map<String, Double> overrides) {
        double pressure = PhysicsValues.require(specification, overrides, "initial_pressure");
        double volume = PhysicsValues.require(specification, overrides, "initial_volume");
        double initialTemperature = PhysicsValues.require(specification, overrides, "initial_temperature");
        double finalTemperature = PhysicsValues.require(specification, overrides, "final_temperature");
        if (!(pressure > 0 && volume > 0 && initialTemperature > 0 && finalTemperature > 0))
            throw new IllegalArgumentException("Ideal-gas process inputs must be positive");
        return new GasProcessParameters(pressure, volume, initialTemperature, finalTemperature);
    }
    public double temperatureRatio() { return finalTemperature / initialTemperature; }
    public double isobaricFinalVolume() { return initialVolume * temperatureRatio(); }
    public double isobaricWork() { return initialPressure * (isobaricFinalVolume() - initialVolume); }
    public double isochoricFinalPressure() { return initialPressure * temperatureRatio(); }
}
