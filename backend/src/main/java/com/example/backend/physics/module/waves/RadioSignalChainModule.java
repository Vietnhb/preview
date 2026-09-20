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
        requirePositiveFinite("wavelength", wavelength);
        requirePositiveFinite("lowerSideband", lowerSideband);
        requirePositiveFinite("upperSideband", upperSideband);
        requireFinite("fmModulationIndex", fmIndex);
        requireFinite("carsonBandwidth", carsonBandwidth);
        requireFinite("attenuationFactor", attenuationFactor);
        requireFinite("receivedAmplitude", receivedAmplitude);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("wavelength", repeated(wavelength, time.size()));
        values.put("lowerSideband", repeated(lowerSideband, time.size()));
        values.put("upperSideband", repeated(upperSideband, time.size()));
        values.put("fmModulationIndex", repeated(fmIndex, time.size()));
        values.put("carsonBandwidth", repeated(carsonBandwidth, time.size()));
        values.put("attenuationFactor", repeated(attenuationFactor, time.size()));
        values.put("receivedAmplitude", repeated(receivedAmplitude, time.size()));
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
        requirePositiveFinite("reference wavelength", wavelength);
        requirePositiveFinite("reference lowerSideband", lowerSideband);
        requirePositiveFinite("reference upperSideband", upperSideband);
        requireFinite("reference fmModulationIndex", beta);
        requireFinite("reference carsonBandwidth", occupiedBandwidth);
        requireFinite("reference attenuationFactor", attenuation);
        requireFinite("reference receivedAmplitude", received);
        return new AnalyticalPoint(Map.of("wavelength", wavelength,
                "lowerSideband", lowerSideband, "upperSideband", upperSideband,
                "fmModulationIndex", beta, "carsonBandwidth", occupiedBandwidth,
                "attenuationFactor", attenuation, "receivedAmplitude", received));
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
