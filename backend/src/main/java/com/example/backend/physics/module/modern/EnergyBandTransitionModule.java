package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed semiconductor band-gap photon-transition model. */
public final class EnergyBandTransitionModule
        implements PhysicsModule<EnergyBandTransitionModule.Parameters> {
    public static final String MODULE_ID = "energy_band_transition";
    public static final String NUMERICAL_SOLVER_ID = "energy_band_transition_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "energy_band_transition_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "valence_band_energy", "J"),
                requireCanonical(quantities, "conduction_band_energy", "J"),
                requireCanonical(quantities, "photon_frequency", "Hz"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double bandGap = parameters.conductionBandEnergy() - parameters.valenceBandEnergy();
        double photonEnergy = PhysicalConstants.PLANCK * parameters.photonFrequency();
        double thresholdWavelength = PhysicalConstants.PLANCK * PhysicalConstants.SPEED_OF_LIGHT / bandGap;
        double transitionAllowed = photonEnergy >= bandGap ? 1.0 : 0.0;
        requirePositiveFinite("bandGap", bandGap);
        requireFinite("photonEnergy", photonEnergy);
        requirePositiveFinite("thresholdWavelength", thresholdWavelength);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("bandGap", repeated(bandGap, time.size()));
        values.put("photonEnergy", repeated(photonEnergy, time.size()));
        values.put("thresholdWavelength", repeated(thresholdWavelength, time.size()));
        values.put("transitionAllowed", repeated(transitionAllowed, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Energy-band reference time must be finite and non-negative");
        }
        BigDecimal valence = BigDecimal.valueOf(parameters.valenceBandEnergy());
        BigDecimal conduction = BigDecimal.valueOf(parameters.conductionBandEnergy());
        BigDecimal gap = conduction.subtract(valence);
        BigDecimal photon = BigDecimal.valueOf(PhysicalConstants.PLANCK)
                .multiply(BigDecimal.valueOf(parameters.photonFrequency()));
        BigDecimal thresholdFrequency = gap.divide(BigDecimal.valueOf(PhysicalConstants.PLANCK), MathContext.DECIMAL128);
        BigDecimal thresholdWavelength = BigDecimal.valueOf(PhysicalConstants.SPEED_OF_LIGHT)
                .divide(thresholdFrequency, MathContext.DECIMAL128);
        double bandGap = gap.doubleValue();
        double photonEnergy = photon.doubleValue();
        double wavelength = thresholdWavelength.doubleValue();
        double allowed = photon.compareTo(gap) >= 0 ? 1.0 : 0.0;
        requirePositiveFinite("reference bandGap", bandGap);
        requireFinite("reference photonEnergy", photonEnergy);
        requirePositiveFinite("reference thresholdWavelength", wavelength);
        return new AnalyticalPoint(Map.of("bandGap", bandGap, "photonEnergy", photonEnergy,
                "thresholdWavelength", wavelength, "transitionAllowed", allowed));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical band-transition quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Band-transition quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Energy-band output must be finite: " + key);
    }

    private static void requirePositiveFinite(String key, double value) {
        requireFinite(key, value);
        if (value <= 0.0) throw new ArithmeticException("Energy-band output must be positive: " + key);
    }

    /** Finite band edges in joules and strictly positive photon frequency. */
    public record Parameters(double valenceBandEnergy, double conductionBandEnergy,
                             double photonFrequency) {
        public Parameters {
            if (!Double.isFinite(valenceBandEnergy) || !Double.isFinite(conductionBandEnergy)
                    || conductionBandEnergy <= valenceBandEnergy
                    || !Double.isFinite(conductionBandEnergy - valenceBandEnergy)
                    || !Double.isFinite(photonFrequency) || photonFrequency <= 0.0) {
                throw new IllegalArgumentException("Band edges and photon frequency are outside the physical domain");
            }
        }
    }
}
