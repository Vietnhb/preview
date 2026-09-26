package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed source/load circuit module; the steady-state output is represented by one t=0 sample. */
public final class SourceInternalResistanceModule implements PhysicsModule<SourceInternalResistanceModule.Parameters> {
    public static final String MODULE_ID = "source_internal_resistance";
    public static final String NUMERICAL_SOLVER_ID = "source_internal_resistance_solver";
    public static final String REFERENCE_SOLVER_ID = "source_internal_resistance_reference";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String numericalSolverId() {
        return NUMERICAL_SOLVER_ID;
    }

    @Override
    public String referenceSolverId() {
        return REFERENCE_SOLVER_ID;
    }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double emf = quantities.require("emf");
        double internalResistance = quantities.require("internal_resistance");
        double loadResistance = quantities.require("load_resistance");
        if (emf <= 0 || internalResistance <= 0 || loadResistance <= 0
                || !Double.isFinite(emf) || !Double.isFinite(internalResistance) || !Double.isFinite(loadResistance)) {
            throw new IllegalArgumentException("Source emf and both resistances must be finite and positive");
        }
        return new Parameters(emf, internalResistance, loadResistance);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        double current = parameters.emf / (parameters.internalResistance + parameters.loadResistance);
        double terminalVoltage = current * parameters.loadResistance;
        double loadPower = current * current * parameters.loadResistance;
        double internalPowerLoss = current * current * parameters.internalResistance;
        double efficiency = terminalVoltage / parameters.emf;
        validateOutputs(current, terminalVoltage, loadPower, internalPowerLoss, efficiency);
        verifyPowerBalance(parameters.emf, current, loadPower, internalPowerLoss);

        Map<String, List<Double>> values = Map.of(
                "current", List.of(current),
                "terminalVoltage", List.of(terminalVoltage),
                "loadPower", List.of(loadPower),
                "internalPowerLoss", List.of(internalPowerLoss),
                "efficiency", List.of(efficiency));
        return new SolverOutput(List.of(0.0), Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireCheckpoint(timeSeconds);
        // The oracle uses Kirchhoff's voltage law and power conservation, rather
        // than reusing the numerical path's resistor-power expressions.
        double current = parameters.emf / (parameters.internalResistance + parameters.loadResistance);
        double terminalVoltage = parameters.emf - current * parameters.internalResistance;
        double loadPower = terminalVoltage * current;
        double inputPower = parameters.emf * current;
        double internalPowerLoss = inputPower - loadPower;
        double efficiency = loadPower / inputPower;
        validateOutputs(current, terminalVoltage, loadPower, internalPowerLoss, efficiency);
        verifyPowerBalance(parameters.emf, current, loadPower, internalPowerLoss);
        return new AnalyticalPoint(Map.of(
                "current", current,
                "terminalVoltage", terminalVoltage,
                "loadPower", loadPower,
                "internalPowerLoss", internalPowerLoss,
                "efficiency", efficiency));
    }

    private static void requireCheckpoint(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Source reference checkpoint must be finite and non-negative");
        }
    }

    private static void validateOutputs(double current, double terminalVoltage, double loadPower,
                                        double internalPowerLoss, double efficiency) {
        if (!Double.isFinite(current) || !Double.isFinite(terminalVoltage) || !Double.isFinite(loadPower)
                || !Double.isFinite(internalPowerLoss) || !Double.isFinite(efficiency)) {
            throw new IllegalArgumentException("Source model produced a non-finite output");
        }
        if (current < 0 || terminalVoltage < 0 || loadPower < 0 || internalPowerLoss < 0
                || efficiency < 0 || efficiency > 1) {
            throw new IllegalStateException("Source output violates passive-load and efficiency bounds");
        }
    }

    private static void verifyPowerBalance(double emf, double current, double loadPower, double internalPowerLoss) {
        double inputPower = emf * current;
        double deliveredPower = loadPower + internalPowerLoss;
        if (!Double.isFinite(inputPower) || !Double.isFinite(deliveredPower)) {
            throw new IllegalArgumentException("Source circuit power is outside the finite numeric domain");
        }
        double scale = Math.max(Math.abs(inputPower), Math.abs(deliveredPower));
        double tolerance = Math.max(1.0e-12 * Math.max(1.0, scale), 32.0 * Math.ulp(scale));
        if (Math.abs(inputPower - deliveredPower) > tolerance) {
            throw new IllegalStateException("Source circuit power balance invariant failed");
        }
    }

    public record Parameters(double emf, double internalResistance, double loadResistance) {
    }
}
