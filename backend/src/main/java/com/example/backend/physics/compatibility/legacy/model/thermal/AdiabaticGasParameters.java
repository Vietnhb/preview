package com.example.backend.physics.compatibility.legacy.model.thermal;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Reversible adiabatic ideal-gas process, PV^gamma = constant. */
public record AdiabaticGasParameters(double initialPressure, double initialVolume,
                                     double finalVolume, double initialTemperature, double gamma) {
    public static AdiabaticGasParameters from(JsonNode specification, Map<String, Double> overrides) {
        double pressure = PhysicsValues.require(specification, overrides, "initial_pressure");
        double initialVolume = PhysicsValues.require(specification, overrides, "initial_volume");
        double finalVolume = PhysicsValues.require(specification, overrides, "final_volume");
        double temperature = PhysicsValues.require(specification, overrides, "initial_temperature");
        double gamma = PhysicsValues.require(specification, overrides, "heat_capacity_ratio");
        if (!(pressure > 0) || !(initialVolume > 0) || !(finalVolume > 0) || !(temperature > 0) || !(gamma > 1))
            throw new IllegalArgumentException("Adiabatic pressure, volumes, temperature positive and gamma > 1 required");
        return new AdiabaticGasParameters(pressure, initialVolume, finalVolume, temperature, gamma);
    }
    public double finalPressure() { return initialPressure * Math.pow(initialVolume / finalVolume, gamma); }
    public double finalTemperature() { return initialTemperature * Math.pow(initialVolume / finalVolume, gamma - 1); }
    public double workByGas() { return (initialPressure * initialVolume - finalPressure() * finalVolume) / (gamma - 1); }
}
