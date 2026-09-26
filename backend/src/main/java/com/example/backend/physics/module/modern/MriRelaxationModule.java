package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed Bloch longitudinal/transverse relaxation approximation. */
public final class MriRelaxationModule implements PhysicsModule<MriRelaxationModule.Parameters> {
    public static final String MODULE_ID = "mri_relaxation";
    public static final String NUMERICAL_SOLVER_ID = "mri_relaxation_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "mri_relaxation_reference_v2";
    private static final String LONGITUDINAL_MAGNETIZATION = "longitudinalMagnetization";
    private static final String TRANSVERSE_MAGNETIZATION = "transverseMagnetization";
    private static final String ECHO_SIGNAL = "echoSignal";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "equilibrium_magnetization", "1"),
                requireCanonical(quantities, "longitudinal_relaxation_time", "s"),
                requireCanonical(quantities, "transverse_relaxation_time", "s"),
                requireCanonical(quantities, "echo_time", "s"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        List<Double> longitudinal = new ArrayList<>(time.size());
        List<Double> transverse = new ArrayList<>(time.size());
        for (double currentTime : time) {
            double mz = parameters.equilibriumMagnetization()
                    * (1.0 - Math.exp(-currentTime / parameters.longitudinalRelaxationTime()));
            double mxy = parameters.equilibriumMagnetization()
                    * Math.exp(-currentTime / parameters.transverseRelaxationTime());
            requireFinite(LONGITUDINAL_MAGNETIZATION, mz);
            requireFinite(TRANSVERSE_MAGNETIZATION, mxy);
            longitudinal.add(mz);
            transverse.add(mxy);
        }
        double echoSignal = parameters.equilibriumMagnetization()
                * Math.exp(-parameters.echoTime() / parameters.transverseRelaxationTime());
        requireFinite(ECHO_SIGNAL, echoSignal);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(LONGITUDINAL_MAGNETIZATION, List.copyOf(longitudinal));
        values.put(TRANSVERSE_MAGNETIZATION, List.copyOf(transverse));
        values.put(ECHO_SIGNAL, java.util.Collections.nCopies(time.size(), echoSignal));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("MRI reference time must be finite and non-negative");
        }
        double mz = parameters.equilibriumMagnetization() == 0.0 ? 0.0
                : -parameters.equilibriumMagnetization()
                * Math.expm1(-timeSeconds / parameters.longitudinalRelaxationTime());
        double mxy = decayedMagnetization(parameters.equilibriumMagnetization(),
                parameters.transverseRelaxationTime(), timeSeconds);
        double echo = decayedMagnetization(parameters.equilibriumMagnetization(),
                parameters.transverseRelaxationTime(), parameters.echoTime());
        requireFinite("reference " + LONGITUDINAL_MAGNETIZATION, mz);
        requireFinite("reference " + TRANSVERSE_MAGNETIZATION, mxy);
        requireFinite("reference " + ECHO_SIGNAL, echo);
        return new AnalyticalPoint(Map.of(LONGITUDINAL_MAGNETIZATION, mz,
                TRANSVERSE_MAGNETIZATION, mxy, ECHO_SIGNAL, echo));
    }

    private static double decayedMagnetization(double initial, double timeConstant, double time) {
        return initial == 0.0 ? 0.0 : Math.exp(Math.log(initial) - time / timeConstant);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical MRI quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("MRI quantity must be finite: " + key);
        return value;
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("MRI output must be finite: " + key);
    }

    /** Normalized magnetization and sequence times after canonical binding. */
    public record Parameters(double equilibriumMagnetization,
                             double longitudinalRelaxationTime,
                             double transverseRelaxationTime,
                             double echoTime) {
        public Parameters {
            if (!Double.isFinite(equilibriumMagnetization) || equilibriumMagnetization < 0.0
                    || !Double.isFinite(longitudinalRelaxationTime) || longitudinalRelaxationTime <= 0.0
                    || !Double.isFinite(transverseRelaxationTime) || transverseRelaxationTime <= 0.0
                    || !Double.isFinite(echoTime) || echoTime < 0.0) {
                throw new IllegalArgumentException("MRI relaxation inputs are outside the physical domain");
            }
        }
    }
}
