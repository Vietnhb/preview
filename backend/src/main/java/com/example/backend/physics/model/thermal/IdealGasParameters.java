package com.example.backend.physics.model.thermal;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical inputs for a quasistatic isothermal ideal-gas process. */
public record IdealGasParameters(
        double amount,
        double temperature,
        double initialVolume,
        double volumeRate,
        double gasConstant) {

    public static IdealGasParameters from(JsonNode specification, Map<String, Double> overrides) {
        double amount = PhysicsValues.require(specification, overrides, "amount_of_substance");
        double temperature = PhysicsValues.require(specification, overrides, "temperature");
        double initialVolume = PhysicsValues.require(specification, overrides, "initial_volume");
        double volumeRate = PhysicsValues.require(specification, overrides, "volume_rate");
        double gasConstant = PhysicsValues.optional(specification, overrides, 8.314462618, "gas_constant");
        if (amount <= 0 || temperature <= 0 || initialVolume <= 0 || gasConstant <= 0) {
            throw new IllegalArgumentException("Ideal-gas amount, temperature, initial volume and gas constant must be positive");
        }
        return new IdealGasParameters(amount, temperature, initialVolume, volumeRate, gasConstant);
    }

    public double pressureScale() { return amount * gasConstant * temperature; }
}
