package com.example.backend.physics.model.thermal;

import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Piecewise heating curve for solid -> liquid -> gas at constant pressure. */
public record PhaseChangeParameters(
        double mass,
        double initialTemperature,
        double meltingTemperature,
        double boilingTemperature,
        double specificHeatSolid,
        double specificHeatLiquid,
        double specificHeatGas,
        double latentHeatFusion,
        double latentHeatVaporization,
        double heatingPower) {

    public static PhaseChangeParameters from(JsonNode specification, Map<String, Double> overrides) {
        double mass = PhysicsValues.require(specification, overrides, "mass");
        double initial = PhysicsValues.require(specification, overrides, "initial_temperature");
        double melting = PhysicsValues.require(specification, overrides, "melting_temperature");
        double boiling = PhysicsValues.require(specification, overrides, "boiling_temperature");
        double solidHeat = PhysicsValues.require(specification, overrides, "specific_heat_solid");
        double liquidHeat = PhysicsValues.require(specification, overrides, "specific_heat_liquid");
        double gasHeat = PhysicsValues.require(specification, overrides, "specific_heat_gas");
        double fusion = PhysicsValues.require(specification, overrides, "latent_heat_fusion");
        double vaporization = PhysicsValues.require(specification, overrides, "latent_heat_vaporization");
        double power = PhysicsValues.require(specification, overrides, "heating_power");
        if (!(mass > 0) || !(solidHeat > 0) || !(liquidHeat > 0) || !(gasHeat > 0)
                || !(fusion > 0) || !(vaporization > 0) || !(power > 0)
                || !Double.isFinite(initial) || !Double.isFinite(melting) || !Double.isFinite(boiling)
                || initial > melting || !(melting < boiling)) {
            throw new IllegalArgumentException("Invalid phase-change parameters or phase ordering");
        }
        return new PhaseChangeParameters(mass, initial, melting, boiling, solidHeat, liquidHeat,
                gasHeat, fusion, vaporization, power);
    }

    public double heatToMeltTemperature() { return mass * specificHeatSolid * (meltingTemperature - initialTemperature); }
    public double heatToFinishFusion() { return heatToMeltTemperature() + mass * latentHeatFusion; }
    public double heatToBoilingTemperature() {
        return heatToFinishFusion() + mass * specificHeatLiquid * (boilingTemperature - meltingTemperature);
    }
    public double heatToFinishVaporization() { return heatToBoilingTemperature() + mass * latentHeatVaporization; }

    public State stateAtEnergy(double heat) {
        double q = Math.max(0, heat);
        double q1 = heatToMeltTemperature();
        double q2 = heatToFinishFusion();
        double q3 = heatToBoilingTemperature();
        double q4 = heatToFinishVaporization();
        if (q < q1) return new State(initialTemperature + q / (mass * specificHeatSolid), "solid", 0, 0);
        if (q < q2) return new State(meltingTemperature, "melting", (q - q1) / (mass * latentHeatFusion), 0);
        if (q < q3) return new State(meltingTemperature + (q - q2) / (mass * specificHeatLiquid), "liquid", 1, 0);
        if (q < q4) return new State(boilingTemperature, "boiling", 1, (q - q3) / (mass * latentHeatVaporization));
        return new State(boilingTemperature + (q - q4) / (mass * specificHeatGas), "gas", 1, 1);
    }

    public record State(double temperature, String phase, double liquidFraction, double vaporFraction) {}
}
