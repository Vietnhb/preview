package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed pulse-echo ultrasound depth and wavelength model. */
public final class UltrasoundImagingModule implements PhysicsModule<UltrasoundImagingModule.Parameters> {
    public static final String MODULE_ID = "ultrasound_imaging";
    public static final String NUMERICAL_SOLVER_ID = "ultrasound_imaging_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "ultrasound_imaging_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "sound_speed", "m/s"),
                requireCanonical(quantities, "frequency", "Hz"),
                requireCanonical(quantities, "echo_time", "s"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double wavelength = parameters.soundSpeed() / parameters.frequency();
        double depth = parameters.soundSpeed() * parameters.echoTime() / 2.0;
        double period = 1.0 / parameters.frequency();
        requirePositiveFinite("wavelength", wavelength);
        requireFinite("depth", depth);
        requirePositiveFinite("period", period);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("wavelength", repeated(wavelength, time.size()));
        values.put("depth", repeated(depth, time.size()));
        values.put("period", repeated(period, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Ultrasound reference time must be finite and non-negative");
        }
        double cyclesPerDistance = parameters.frequency() / parameters.soundSpeed();
        double wavelength = 1.0 / cyclesPerDistance;
        double roundTripDurationPerDistance = 2.0 / parameters.soundSpeed();
        double depth = parameters.echoTime() / roundTripDurationPerDistance;
        double period = wavelength / parameters.soundSpeed();
        requirePositiveFinite("reference wavelength", wavelength);
        requireFinite("reference depth", depth);
        requirePositiveFinite("reference period", period);
        return new AnalyticalPoint(Map.of("wavelength", wavelength, "depth", depth, "period", period));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical ultrasound quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Ultrasound quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Ultrasound output must be finite: " + key);
    }

    private static void requirePositiveFinite(String key, double value) {
        requireFinite(key, value);
        if (value <= 0.0) throw new ArithmeticException("Ultrasound output must be positive: " + key);
    }

    /** Canonical sound speed, ultrasound frequency and round-trip echo time. */
    public record Parameters(double soundSpeed, double frequency, double echoTime) {
        public Parameters {
            if (!Double.isFinite(soundSpeed) || soundSpeed <= 0.0
                    || !Double.isFinite(frequency) || frequency <= 0.0
                    || !Double.isFinite(echoTime) || echoTime < 0.0) {
                throw new IllegalArgumentException("Ultrasound speed, frequency and echo time are outside the physical domain");
            }
        }
    }
}
