package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed ideal AM radio carrier and sideband relationships. */
public final class RadioCommunicationModule implements PhysicsModule<RadioCommunicationModule.Parameters> {
    public static final String MODULE_ID = "radio_communication";
    public static final String NUMERICAL_SOLVER_ID = "radio_communication_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "radio_communication_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "carrier_frequency", "Hz"),
                requireCanonical(quantities, "modulation_frequency", "Hz"),
                requireCanonical(quantities, "modulation_index", "1"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double wavelength = PhysicalConstants.SPEED_OF_LIGHT / parameters.carrierFrequency();
        double period = 1.0 / parameters.carrierFrequency();
        double angularFrequency = 2.0 * Math.PI * parameters.carrierFrequency();
        double lowerSideband = parameters.carrierFrequency() - parameters.modulationFrequency();
        double upperSideband = parameters.carrierFrequency() + parameters.modulationFrequency();
        requirePositiveFinite("wavelength", wavelength);
        requirePositiveFinite("period", period);
        requirePositiveFinite("angularFrequency", angularFrequency);
        requirePositiveFinite("lowerSideband", lowerSideband);
        requirePositiveFinite("upperSideband", upperSideband);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("wavelength", repeated(wavelength, time.size()));
        values.put("period", repeated(period, time.size()));
        values.put("angularFrequency", repeated(angularFrequency, time.size()));
        values.put("lowerSideband", repeated(lowerSideband, time.size()));
        values.put("upperSideband", repeated(upperSideband, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireReferenceTime(timeSeconds);
        double wavelength = PhysicalConstants.SPEED_OF_LIGHT / parameters.carrierFrequency();
        double period = wavelength / PhysicalConstants.SPEED_OF_LIGHT;
        double angularFrequency = 2.0 * Math.PI / period;
        double sidebandSpacing = parameters.modulationFrequency();
        double lowerSideband = parameters.carrierFrequency() - sidebandSpacing;
        double upperSideband = parameters.carrierFrequency() + sidebandSpacing;
        requirePositiveFinite("reference wavelength", wavelength);
        requirePositiveFinite("reference period", period);
        requirePositiveFinite("reference angularFrequency", angularFrequency);
        requirePositiveFinite("reference lowerSideband", lowerSideband);
        requirePositiveFinite("reference upperSideband", upperSideband);
        return new AnalyticalPoint(Map.of("wavelength", wavelength, "period", period,
                "angularFrequency", angularFrequency, "lowerSideband", lowerSideband,
                "upperSideband", upperSideband));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical radio quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Radio quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requirePositiveFinite(String key, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new ArithmeticException("Radio communication output must be positive and finite: " + key);
        }
    }

    private static void requireReferenceTime(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Radio communication reference time must be finite and non-negative");
        }
    }

    /** Positive carrier/modulation frequencies and bounded AM modulation depth. */
    public record Parameters(double carrierFrequency, double modulationFrequency, double modulationIndex) {
        public Parameters {
            if (!Double.isFinite(carrierFrequency) || carrierFrequency <= 0.0
                    || !Double.isFinite(modulationFrequency) || modulationFrequency <= 0.0
                    || modulationFrequency >= carrierFrequency
                    || !Double.isFinite(modulationIndex) || modulationIndex < 0.0 || modulationIndex > 1.0) {
                throw new IllegalArgumentException("Radio carrier, modulation frequency or modulation index is outside the AM domain");
            }
        }
    }
}
