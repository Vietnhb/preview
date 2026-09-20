package com.example.backend.physics.module.thermal;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed solid-to-liquid-to-gas heating curve with a separately derived oracle. */
public final class PhaseChangeModule implements PhysicsModule<PhaseChangeModule.Parameters> {
    public static final String MODULE_ID = "phase_change";
    public static final String NUMERICAL_SOLVER_ID = "phase_change_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "phase_change_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) {
            throw new IllegalArgumentException("Canonical phase-change quantities are required");
        }
        requireUnit(quantities, "mass", "kg");
        requireUnit(quantities, "initial_temperature", "K");
        requireUnit(quantities, "melting_temperature", "K");
        requireUnit(quantities, "boiling_temperature", "K");
        requireUnit(quantities, "specific_heat_solid", "J/(kg*K)");
        requireUnit(quantities, "specific_heat_liquid", "J/(kg*K)");
        requireUnit(quantities, "specific_heat_gas", "J/(kg*K)");
        requireUnit(quantities, "latent_heat_fusion", "J/kg");
        requireUnit(quantities, "latent_heat_vaporization", "J/kg");
        requireUnit(quantities, "heating_power", "W");
        return new Parameters(
                quantities.require("mass"),
                quantities.require("initial_temperature"),
                quantities.require("melting_temperature"),
                quantities.require("boiling_temperature"),
                quantities.require("specific_heat_solid"),
                quantities.require("specific_heat_liquid"),
                quantities.require("specific_heat_gas"),
                quantities.require("latent_heat_fusion"),
                quantities.require("latent_heat_vaporization"),
                quantities.require("heating_power"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireParameters(parameters);
        if (clock == null) {
            throw new IllegalArgumentException("Phase-change simulation clock is required");
        }

        double solidHeatCapacity = parameters.mass() * parameters.specificHeatSolid();
        double liquidHeatCapacity = parameters.mass() * parameters.specificHeatLiquid();
        double gasHeatCapacity = parameters.mass() * parameters.specificHeatGas();
        double fusionEnergy = parameters.mass() * parameters.latentHeatFusion();
        double vaporizationEnergy = parameters.mass() * parameters.latentHeatVaporization();
        requirePositiveFinite("solid heat capacity", solidHeatCapacity);
        requirePositiveFinite("liquid heat capacity", liquidHeatCapacity);
        requirePositiveFinite("gas heat capacity", gasHeatCapacity);
        requirePositiveFinite("fusion energy", fusionEnergy);
        requirePositiveFinite("vaporization energy", vaporizationEnergy);

        double meltTemperatureEnergy = solidHeatCapacity
                * (parameters.meltingTemperature() - parameters.initialTemperature());
        double finishFusionEnergy = meltTemperatureEnergy + fusionEnergy;
        double boilTemperatureEnergy = finishFusionEnergy + liquidHeatCapacity
                * (parameters.boilingTemperature() - parameters.meltingTemperature());
        double finishVaporizationEnergy = boilTemperatureEnergy + vaporizationEnergy;
        validateThresholds(meltTemperatureEnergy, finishFusionEnergy,
                boilTemperatureEnergy, finishVaporizationEnergy);

        List<Double> time = clock.sampleTimes();
        List<Double> heat = new ArrayList<>(time.size());
        List<Double> temperature = new ArrayList<>(time.size());
        List<Double> liquidFraction = new ArrayList<>(time.size());
        List<Double> vaporFraction = new ArrayList<>(time.size());
        for (double currentTime : time) {
            double currentHeat = parameters.heatingPower() * currentTime;
            requireNonNegativeFinite("heatAdded", currentHeat);
            State state = numericalState(parameters, currentHeat, solidHeatCapacity,
                    liquidHeatCapacity, gasHeatCapacity, fusionEnergy, vaporizationEnergy,
                    meltTemperatureEnergy, finishFusionEnergy, boilTemperatureEnergy,
                    finishVaporizationEnergy);
            validateState(state);
            heat.add(currentHeat);
            temperature.add(state.temperature());
            liquidFraction.add(state.liquidFraction());
            vaporFraction.add(state.vaporFraction());
        }

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("temperature", temperature);
        values.put("heatAdded", heat);
        values.put("liquidFraction", liquidFraction);
        values.put("vaporFraction", vaporFraction);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireParameters(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        double heatAdded = parameters.heatingPower() * timeSeconds;
        requireNonNegativeFinite("reference heatAdded", heatAdded);

        // The oracle uses energy per unit mass, while the numerical path uses
        // total specimen capacities and energies. This independently expresses
        // each sensible/latent transition instead of reusing its threshold logic.
        double specificEnergy = heatAdded / parameters.mass();
        double specificHeatToMelt = parameters.specificHeatSolid()
                * (parameters.meltingTemperature() - parameters.initialTemperature());
        double specificEnergyAfterFusion = specificHeatToMelt + parameters.latentHeatFusion();
        double specificEnergyToBoil = specificEnergyAfterFusion + parameters.specificHeatLiquid()
                * (parameters.boilingTemperature() - parameters.meltingTemperature());
        double specificEnergyAfterVaporization = specificEnergyToBoil
                + parameters.latentHeatVaporization();
        requireNonNegativeFinite("reference specific energy", specificEnergy);
        validateThresholds(specificHeatToMelt, specificEnergyAfterFusion,
                specificEnergyToBoil, specificEnergyAfterVaporization);

        double temperature;
        double liquidFraction;
        double vaporFraction;
        if (specificEnergy < specificHeatToMelt) {
            temperature = parameters.initialTemperature()
                    + specificEnergy / parameters.specificHeatSolid();
            liquidFraction = 0.0;
            vaporFraction = 0.0;
        } else if (specificEnergy < specificEnergyAfterFusion) {
            temperature = parameters.meltingTemperature();
            liquidFraction = (specificEnergy - specificHeatToMelt) / parameters.latentHeatFusion();
            vaporFraction = 0.0;
        } else if (specificEnergy < specificEnergyToBoil) {
            temperature = parameters.meltingTemperature()
                    + (specificEnergy - specificEnergyAfterFusion) / parameters.specificHeatLiquid();
            liquidFraction = 1.0;
            vaporFraction = 0.0;
        } else if (specificEnergy < specificEnergyAfterVaporization) {
            temperature = parameters.boilingTemperature();
            vaporFraction = (specificEnergy - specificEnergyToBoil)
                    / parameters.latentHeatVaporization();
            liquidFraction = 1.0 - vaporFraction;
        } else {
            temperature = parameters.boilingTemperature()
                    + (specificEnergy - specificEnergyAfterVaporization) / parameters.specificHeatGas();
            liquidFraction = 0.0;
            vaporFraction = 1.0;
        }
        State state = new State(temperature, liquidFraction, vaporFraction);
        validateState(state);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("temperature", state.temperature());
        values.put("heatAdded", heatAdded);
        values.put("liquidFraction", state.liquidFraction());
        values.put("vaporFraction", state.vaporFraction());
        return new AnalyticalPoint(values);
    }

    private static State numericalState(Parameters p, double heat, double solidHeatCapacity,
                                        double liquidHeatCapacity, double gasHeatCapacity,
                                        double fusionEnergy, double vaporizationEnergy,
                                        double meltTemperatureEnergy, double finishFusionEnergy,
                                        double boilTemperatureEnergy, double finishVaporizationEnergy) {
        if (heat < meltTemperatureEnergy) {
            return new State(p.initialTemperature() + heat / solidHeatCapacity, 0.0, 0.0);
        }
        if (heat < finishFusionEnergy) {
            return new State(p.meltingTemperature(),
                    (heat - meltTemperatureEnergy) / fusionEnergy, 0.0);
        }
        if (heat < boilTemperatureEnergy) {
            return new State(p.meltingTemperature() + (heat - finishFusionEnergy) / liquidHeatCapacity,
                    1.0, 0.0);
        }
        if (heat < finishVaporizationEnergy) {
            double vaporFraction = (heat - boilTemperatureEnergy) / vaporizationEnergy;
            return new State(p.boilingTemperature(), 1.0 - vaporFraction, vaporFraction);
        }
        return new State(p.boilingTemperature() + (heat - finishVaporizationEnergy) / gasHeatCapacity,
                0.0, 1.0);
    }

    private static void validateThresholds(double first, double second, double third, double fourth) {
        requireNonNegativeFinite("energy to reach melting temperature", first);
        requirePositiveFinite("energy to finish fusion", second);
        requirePositiveFinite("energy to reach boiling temperature", third);
        requirePositiveFinite("energy to finish vaporization", fourth);
        if (!(first < second && second < third && third < fourth)) {
            throw new IllegalArgumentException("Phase-change energy thresholds must remain strictly ordered");
        }
    }

    private static void validateState(State state) {
        requireNonNegativeFinite("temperature", state.temperature());
        requireNonNegativeFinite("liquidFraction", state.liquidFraction());
        requireNonNegativeFinite("vaporFraction", state.vaporFraction());
        if (state.liquidFraction() > 1.0 || state.vaporFraction() > 1.0) {
            throw new IllegalArgumentException("Phase-change fractions must remain between zero and one");
        }
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for '" + key + "' must be " + expectedUnit);
        }
    }

    private static void requireParameters(Parameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Phase-change parameters are required");
        }
    }

    private static void requireNonNegativeFinite(String key, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Phase-change calculation produced invalid " + key);
        }
    }

    private static void requirePositiveFinite(String key, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Phase-change calculation produced invalid " + key);
        }
    }

    private record State(double temperature, double liquidFraction, double vaporFraction) { }

    /** Immutable physical input state bound once from canonical quantities. */
    public record Parameters(double mass, double initialTemperature, double meltingTemperature,
                             double boilingTemperature, double specificHeatSolid,
                             double specificHeatLiquid, double specificHeatGas,
                             double latentHeatFusion, double latentHeatVaporization,
                             double heatingPower) {
        public Parameters {
            if (!Double.isFinite(mass) || mass <= 0.0
                    || !Double.isFinite(initialTemperature) || initialTemperature < 0.0
                    || !Double.isFinite(meltingTemperature) || meltingTemperature < initialTemperature
                    || !Double.isFinite(boilingTemperature) || boilingTemperature <= meltingTemperature
                    || !Double.isFinite(specificHeatSolid) || specificHeatSolid <= 0.0
                    || !Double.isFinite(specificHeatLiquid) || specificHeatLiquid <= 0.0
                    || !Double.isFinite(specificHeatGas) || specificHeatGas <= 0.0
                    || !Double.isFinite(latentHeatFusion) || latentHeatFusion <= 0.0
                    || !Double.isFinite(latentHeatVaporization) || latentHeatVaporization <= 0.0
                    || !Double.isFinite(heatingPower) || heatingPower <= 0.0) {
                throw new IllegalArgumentException(
                        "Phase-change inputs require non-negative ordered absolute temperatures and positive finite material, latent-heat, and power values");
            }
        }
    }
}
