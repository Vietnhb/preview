package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.circuits.AcWaveformParameters;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.TimeSeriesOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed sinusoidal AC module; its reference path calculates from primitive inputs independently. */
public final class AcWaveformModule implements PhysicsModule<AcWaveformParameters> {
    public static final String MODULE_ID = "ac_waveform";
    public static final String NUMERICAL_SOLVER_ID = "ac_waveform_solver";
    public static final String REFERENCE_SOLVER_ID = "ac_waveform_reference";
    private static final String VOLTAGE = "voltage";
    private static final String RMS_VOLTAGE = "rmsVoltage";

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
    public AcWaveformParameters bind(CanonicalQuantityBag quantities) {
        return AcWaveformParameters.from(quantities);
    }

    @Override
    public SolverOutput solve(AcWaveformParameters parameters, SimulationClock clock) {
        List<Double> time = clock.sampleTimes();
        List<Double> voltage = new ArrayList<>(time.size());
        List<Double> rmsVoltage = new ArrayList<>(time.size());
        for (double t : time) {
            voltage.add(parameters.voltage(t));
            rmsVoltage.add(parameters.rmsVoltage());
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(VOLTAGE, List.copyOf(voltage));
        values.put(RMS_VOLTAGE, List.copyOf(rmsVoltage));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public boolean nativeTypedOutput() {
        return true;
    }

    @Override
    public PhysicsOutputFrame solveTyped(AcWaveformParameters parameters, SimulationClock clock,
                                         PhysicsOutputContract contract) {
        List<Double> time = clock.sampleTimes();
        List<Double> voltage = new ArrayList<>(time.size());
        List<Double> rmsVoltage = new ArrayList<>(time.size());
        for (double t : time) {
            voltage.add(parameters.voltage(t));
            rmsVoltage.add(parameters.rmsVoltage());
        }
        return new PhysicsOutputFrame(time, List.of(
                new TimeSeriesOutput(VOLTAGE, requiredUnit(contract, VOLTAGE), time, voltage),
                new TimeSeriesOutput(RMS_VOLTAGE, requiredUnit(contract, RMS_VOLTAGE), time, rmsVoltage)));
    }

    private static String requiredUnit(PhysicsOutputContract contract, String key) {
        if (contract == null || contract.outputs().get(key) == null) {
            throw new IllegalArgumentException("Typed output contract is missing " + key);
        }
        return contract.outputs().get(key).unit();
    }

    @Override
    public AnalyticalPoint referenceAt(AcWaveformParameters parameters, double timeSeconds) {
        // Deliberately recompute from the primitive parameters; the oracle does not call
        // AcWaveformParameters.voltage(), angularFrequency(), or rmsVoltage().
        double clampedTime = Math.max(0, timeSeconds);
        double angularFrequency = 2.0 * Math.PI * parameters.frequency();
        double independentVoltage = parameters.peakVoltage()
                * Math.sin(angularFrequency * clampedTime + parameters.phase());
        double independentRmsVoltage = parameters.peakVoltage() / Math.sqrt(2.0);
        return new AnalyticalPoint(Map.of(
                "voltage", independentVoltage,
                "rmsVoltage", independentRmsVoltage));
    }
}
