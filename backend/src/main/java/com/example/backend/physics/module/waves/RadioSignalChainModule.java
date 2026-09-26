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

/** Typed AM/FM signal-chain model with logarithmic path loss. */
public final class RadioSignalChainModule implements PhysicsModule<RadioSignalChainModule.Parameters> {
    public static final String MODULE_ID = "radio_signal_chain";
    public static final String NUMERICAL_SOLVER_ID = "radio_signal_chain_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "radio_signal_chain_reference_v2";
    private static final String WAVELENGTH = "wavelength";
    private static final String LOWER_SIDEBAND = "lowerSideband";
    private static final String UPPER_SIDEBAND = "upperSideband";
    private static final String FM_MODULATION_INDEX = "fmModulationIndex";
    private static final String CARSON_BANDWIDTH = "carsonBandwidth";
    private static final String ATTENUATION_FACTOR = "attenuationFactor";
    private static final String RECEIVED_AMPLITUDE = "receivedAmplitude";
    private static final String REFERENCE_PREFIX = "reference ";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "carrier_frequency", "Hz"),
                requireCanonical(quantities, "modulation_frequency", "Hz"),
                requireCanonical(quantities, "frequency_deviation", "Hz"),
                requireCanonical(quantities, "modulation_index", "1"),
                requireCanonical(quantities, "signal_amplitude", "V"),
                requireCanonical(quantities, "path_length", "m"),
                requireCanonical(quantities, "attenuation_db_per_meter", "dB/m"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double wavelength = PhysicalConstants.SPEED_OF_LIGHT / parameters.carrierFrequency();
        double lowerSideband = parameters.carrierFrequency() - parameters.modulationFrequency();
        double upperSideband = parameters.carrierFrequency() + parameters.modulationFrequency();
        double fmIndex = parameters.frequencyDeviation() / parameters.modulationFrequency();
        double carsonBandwidth = 2.0 * (parameters.frequencyDeviation() + parameters.modulationFrequency());
        double attenuationDb = parameters.attenuationDbPerMeter() * parameters.pathLength();
        double attenuationFactor = Math.pow(10.0, -attenuationDb / 20.0);
        double receivedAmplitude = parameters.signalAmplitude() * attenuationFactor;
        requirePositiveFinite(WAVELENGTH, wavelength);
        requirePositiveFinite(LOWER_SIDEBAND, lowerSideband);
        requirePositiveFinite(UPPER_SIDEBAND, upperSideband);
        requireFinite(FM_MODULATION_INDEX, fmIndex);
        requireFinite(CARSON_BANDWIDTH, carsonBandwidth);
        requireFinite(ATTENUATION_FACTOR, attenuationFactor);
        requireFinite(RECEIVED_AMPLITUDE, receivedAmplitude);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(WAVELENGTH, repeated(wavelength, time.size()));
        values.put(LOWER_SIDEBAND, repeated(lowerSideband, time.size()));
        values.put(UPPER_SIDEBAND, repeated(upperSideband, time.size()));
        values.put(FM_MODULATION_INDEX, repeated(fmIndex, time.size()));
        values.put(CARSON_BANDWIDTH, repeated(carsonBandwidth, time.size()));
        values.put(ATTENUATION_FACTOR, repeated(attenuationFactor, time.size()));
        values.put(RECEIVED_AMPLITUDE, repeated(receivedAmplitude, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Radio signal-chain reference time must be finite and non-negative");
        }
        double wavelength = 1.0 / (parameters.carrierFrequency() / PhysicalConstants.SPEED_OF_LIGHT);
        double beta = parameters.frequencyDeviation() / parameters.modulationFrequency();
        double occupiedBandwidth = 2.0 * parameters.modulationFrequency() * (1.0 + beta);
        double pathLossExponent = -Math.log(10.0) * parameters.attenuationDbPerMeter()
                * parameters.pathLength() / 20.0;
        double attenuation = Math.exp(pathLossExponent);
        double received = parameters.signalAmplitude() * attenuation;
        double lowerSideband = parameters.carrierFrequency() - parameters.modulationFrequency();
        double upperSideband = parameters.carrierFrequency() + parameters.modulationFrequency();
        requirePositiveFinite(REFERENCE_PREFIX + WAVELENGTH, wavelength);
        requirePositiveFinite(REFERENCE_PREFIX + LOWER_SIDEBAND, lowerSideband);
        requirePositiveFinite(REFERENCE_PREFIX + UPPER_SIDEBAND, upperSideband);
        requireFinite(REFERENCE_PREFIX + FM_MODULATION_INDEX, beta);
        requireFinite(REFERENCE_PREFIX + CARSON_BANDWIDTH, occupiedBandwidth);
        requireFinite(REFERENCE_PREFIX + ATTENUATION_FACTOR, attenuation);
        requireFinite(REFERENCE_PREFIX + RECEIVED_AMPLITUDE, received);
        return new AnalyticalPoint(Map.of(WAVELENGTH, wavelength,
                LOWER_SIDEBAND, lowerSideband, UPPER_SIDEBAND, upperSideband,
                FM_MODULATION_INDEX, beta, CARSON_BANDWIDTH, occupiedBandwidth,
                ATTENUATION_FACTOR, attenuation, RECEIVED_AMPLITUDE, received));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical radio-chain quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Radio-chain quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Radio signal-chain output must be finite: " + key);
    }

    private static void requirePositiveFinite(String key, double value) {
        requireFinite(key, value);
        if (value <= 0.0) throw new ArithmeticException("Radio signal-chain output must be positive: " + key);
    }

    /** Canonical RF values after quantity compilation and SI unit normalization. */
    public record Parameters(double carrierFrequency, double modulationFrequency,
                             double frequencyDeviation, double modulationIndex,
                             double signalAmplitude, double pathLength,
                             double attenuationDbPerMeter) {
        public Parameters {
            if (!Double.isFinite(carrierFrequency) || carrierFrequency <= 0.0
                    || !Double.isFinite(modulationFrequency) || modulationFrequency <= 0.0
                    || modulationFrequency >= carrierFrequency
                    || !Double.isFinite(frequencyDeviation) || frequencyDeviation < 0.0
                    || !Double.isFinite(modulationIndex) || modulationIndex < 0.0
                    || !Double.isFinite(signalAmplitude) || signalAmplitude < 0.0
                    || !Double.isFinite(pathLength) || pathLength < 0.0
                    || !Double.isFinite(attenuationDbPerMeter) || attenuationDbPerMeter < 0.0) {
                throw new IllegalArgumentException("Radio signal-chain inputs are outside the physical domain");
            }
        }
    }
}
