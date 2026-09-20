package com.example.backend.physics.model.thermal;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical inputs for an ideal two-body calorimetry mixture. */
public record CalorimetryParameters(double mass1, double specificHeat1, double initialTemperature1,
                                    double mass2, double specificHeat2, double initialTemperature2) {
    public static CalorimetryParameters from(JsonNode specification, Map<String, Double> overrides) {
        double m1 = PhysicsValues.require(specification, overrides, "mass_1");
        double c1 = PhysicsValues.require(specification, overrides, "specific_heat_1");
        double t1 = PhysicsValues.require(specification, overrides, "initial_temperature_1");
        double m2 = PhysicsValues.require(specification, overrides, "mass_2");
        double c2 = PhysicsValues.require(specification, overrides, "specific_heat_2");
        double t2 = PhysicsValues.require(specification, overrides, "initial_temperature_2");
        if (!(m1 > 0 && c1 > 0 && m2 > 0 && c2 > 0)
                || !Double.isFinite(t1) || !Double.isFinite(t2) || t1 <= 0 || t2 <= 0) {
            throw new IllegalArgumentException("Masses, specific heats and absolute temperatures must be positive");
        }
        return new CalorimetryParameters(m1, c1, t1, m2, c2, t2);
    }

    public double equilibriumTemperature() {
        return (mass1 * specificHeat1 * initialTemperature1 + mass2 * specificHeat2 * initialTemperature2)
                / (mass1 * specificHeat1 + mass2 * specificHeat2);
    }

    public double heatToBody1() { return mass1 * specificHeat1 * (equilibriumTemperature() - initialTemperature1); }
    public double heatToBody2() { return mass2 * specificHeat2 * (equilibriumTemperature() - initialTemperature2); }
}
