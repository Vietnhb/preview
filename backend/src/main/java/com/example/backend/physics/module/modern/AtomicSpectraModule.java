package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed hydrogen emission spectrum using Rydberg wavelengths and energy-level oracles. */
public final class AtomicSpectraModule implements PhysicsModule<AtomicSpectraModule.Parameters> {
    public static final String MODULE_ID = "atomic_spectra";
    public static final String NUMERICAL_SOLVER_ID = "atomic_spectra_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "atomic_spectra_reference_v2";
    private static final double RYDBERG_PER_METRE = 10_973_731.568160;
    private static final int MAX_LEVEL = 1000;
    private static final MathContext REFERENCE_PRECISION = MathContext.DECIMAL128;
    private static final String WAVELENGTH = "wavelength";
    private static final String FREQUENCY = "frequency";
    private static final String PHOTON_ENERGY = "photonEnergy";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double initial = requireCanonical(quantities, "initial_level", "1");
        double terminal = requireCanonical(quantities, "final_level", "1");
        if (initial != Math.rint(initial) || terminal != Math.rint(terminal)
                || initial > MAX_LEVEL || terminal > MAX_LEVEL) {
            throw new IllegalArgumentException("Hydrogen levels must be integers in [1,1000]");
        }
        return new Parameters((int) initial, (int) terminal);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double inverseWavelength = RYDBERG_PER_METRE
                * (1.0 / square(parameters.finalLevel()) - 1.0 / square(parameters.initialLevel()));
        double wavelength = 1.0 / inverseWavelength;
        double frequency = PhysicalConstants.SPEED_OF_LIGHT / wavelength;
        double photonEnergy = PhysicalConstants.PLANCK * frequency;
        requirePositiveFinite("inverseWavelength", inverseWavelength);
        requirePositiveFinite(WAVELENGTH, wavelength);
        requirePositiveFinite(FREQUENCY, frequency);
        requirePositiveFinite(PHOTON_ENERGY, photonEnergy);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(WAVELENGTH, repeated(wavelength, time.size()));
        values.put(FREQUENCY, repeated(frequency, time.size()));
        values.put(PHOTON_ENERGY, repeated(photonEnergy, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Atomic-spectrum reference time must be finite and non-negative");
        }
        BigDecimal initial = BigDecimal.valueOf(parameters.initialLevel());
        BigDecimal terminal = BigDecimal.valueOf(parameters.finalLevel());
        BigDecimal initialSquare = initial.multiply(initial);
        BigDecimal terminalSquare = terminal.multiply(terminal);
        BigDecimal levelGap = BigDecimal.ONE.divide(terminalSquare, REFERENCE_PRECISION)
                .subtract(BigDecimal.ONE.divide(initialSquare, REFERENCE_PRECISION));
        BigDecimal h = BigDecimal.valueOf(PhysicalConstants.PLANCK);
        BigDecimal c = BigDecimal.valueOf(PhysicalConstants.SPEED_OF_LIGHT);
        BigDecimal rydberg = BigDecimal.valueOf(RYDBERG_PER_METRE);
        BigDecimal photonEnergy = h.multiply(c).multiply(rydberg).multiply(levelGap);
        BigDecimal wavelength = h.multiply(c).divide(photonEnergy, REFERENCE_PRECISION);
        BigDecimal frequency = photonEnergy.divide(h, REFERENCE_PRECISION);
        double energy = photonEnergy.doubleValue();
        double lambda = wavelength.doubleValue();
        double hz = frequency.doubleValue();
        requirePositiveFinite("reference " + PHOTON_ENERGY, energy);
        requirePositiveFinite("reference " + WAVELENGTH, lambda);
        requirePositiveFinite("reference " + FREQUENCY, hz);
        return new AnalyticalPoint(Map.of(WAVELENGTH, lambda, FREQUENCY, hz, PHOTON_ENERGY, energy));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical atomic-spectrum quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Atomic level must be finite: " + key);
        return value;
    }

    private static double square(double level) {
        return level * level;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requirePositiveFinite(String key, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new ArithmeticException("Atomic-spectrum output must be positive and finite: " + key);
        }
    }

    /** Bounded integer hydrogen energy levels after canonicalization. */
    public record Parameters(int initialLevel, int finalLevel) {
        public Parameters {
            if (finalLevel < 1 || initialLevel <= finalLevel || initialLevel > MAX_LEVEL) {
                throw new IllegalArgumentException("Hydrogen levels require 1 <= final < initial <= 1000");
            }
        }
    }
}
