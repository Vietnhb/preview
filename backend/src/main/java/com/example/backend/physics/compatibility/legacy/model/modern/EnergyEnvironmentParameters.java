package com.example.backend.physics.compatibility.legacy.model.modern;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Energy-mix and emissions accounting model for environmental projects. */
public record EnergyEnvironmentParameters(double demand, double renewableFraction,
                                          double fossilEmissionFactor, double renewableEmissionFactor,
                                          double conversionEfficiency) {
    public static EnergyEnvironmentParameters from(JsonNode specification, Map<String, Double> overrides) {
        double demand = PhysicsValues.require(specification, overrides, "energy_demand");
        double renewable = PhysicsValues.require(specification, overrides, "renewable_fraction");
        double fossilFactor = PhysicsValues.require(specification, overrides, "fossil_emission_factor");
        double renewableFactor = PhysicsValues.require(specification, overrides, "renewable_emission_factor");
        double efficiency = PhysicsValues.require(specification, overrides, "conversion_efficiency");
        if (!(demand >= 0) || renewable < 0 || renewable > 1 || fossilFactor < 0 || renewableFactor < 0
                || efficiency <= 0 || efficiency > 1) {
            throw new IllegalArgumentException("Energy-environment parameters are invalid");
        }
        return new EnergyEnvironmentParameters(demand, renewable, fossilFactor, renewableFactor, efficiency);
    }
    public double renewableEnergy() { return demand * renewableFraction; }
    public double fossilEnergy() { return demand * (1 - renewableFraction); }
    public double emissions() { return renewableEnergy() * renewableEmissionFactor + fossilEnergy() * fossilEmissionFactor; }
    public double usefulEnergy() { return demand * conversionEfficiency; }
    public double avoidedEmissionsVsFossil() { return demand * fossilEmissionFactor - emissions(); }
}
