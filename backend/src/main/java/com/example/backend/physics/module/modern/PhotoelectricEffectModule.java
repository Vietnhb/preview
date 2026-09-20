package com.example.backend.physics.module.modern;

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

/** Typed Einstein photoelectric-effect model with an independently expressed photon-energy oracle. */
public final class PhotoelectricEffectModule implements PhysicsModule<PhotoelectricEffectModule.Parameters> {
    public static final String MODULE_ID = "photoelectric_effect";
    public static final String NUMERICAL_SOLVER_ID = "photoelectric_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "photoelectric_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "photon_frequency", "Hz"),
                requireCanonical(quantities, "work_function", "J"),
                requireCanonical(quantities, "electron_charge", "C"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> times = Objects.requireNonNull(clock, "clock").sampleTimes();

        double photonEnergy = PhysicalConstants.PLANCK * parameters.photonFrequency();
        double energyAboveThreshold = photonEnergy - parameters.workFunction();
        double maximumKineticEnergy = Math.max(0.0, energyAboveThreshold);
        double stoppingPotential = maximumKineticEnergy / parameters.electronCharge();
        double wavelength = PhysicalConstants.SPEED_OF_LIGHT / parameters.photonFrequency();
        double emissionOccurs = photonEnergy >= parameters.workFunction() ? 1.0 : 0.0;
        requireFiniteResults(photonEnergy, maximumKineticEnergy, stoppingPotential, wavelength, emissionOccurs);
        if (wavelength <= 0.0) {
            throw new ArithmeticException("Photoelectric wavelength is outside the positive numeric domain");
        }

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("photonEnergy", repeated(photonEnergy, times.size()));
        values.put("maximumKineticEnergy", repeated(maximumKineticEnergy, times.size()));
        values.put("stoppingPotential", repeated(stoppingPotential, times.size()));
        values.put("wavelength", repeated(wavelength, times.size()));
        values.put("emissionOccurs", repeated(emissionOccurs, times.size()));
        return new SolverOutput(times, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Photoelectric reference time must be finite and non-negative");
        }

        // The oracle obtains the photon energy from its wavelength, then applies
        // the energy balance. This is separate from the solver's direct h*f path.
        double wavelength = PhysicalConstants.SPEED_OF_LIGHT / parameters.photonFrequency();
        double photonEnergy = PhysicalConstants.PLANCK * PhysicalConstants.SPEED_OF_LIGHT / wavelength;
        double excessEnergy = photonEnergy - parameters.workFunction();
        boolean emissionOccurs = excessEnergy >= 0.0;
        double maximumKineticEnergy = emissionOccurs ? excessEnergy : 0.0;
        double stoppingPotential = maximumKineticEnergy / parameters.electronCharge();
        requireFiniteResults(photonEnergy, maximumKineticEnergy, stoppingPotential, wavelength,
                emissionOccurs ? 1.0 : 0.0);
        if (wavelength <= 0.0) {
            throw new ArithmeticException("Photoelectric reference wavelength is outside the positive numeric domain");
        }

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("photonEnergy", photonEnergy);
        values.put("maximumKineticEnergy", maximumKineticEnergy);
        values.put("stoppingPotential", stoppingPotential);
        values.put("wavelength", wavelength);
        values.put("emissionOccurs", emissionOccurs ? 1.0 : 0.0);
        return new AnalyticalPoint(values);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        double value = quantities.require(key);
        String actualUnit = quantities.unit(key);
        if (!expectedUnit.equals(actualUnit)) {
            throw new IllegalArgumentException("Photoelectric quantity " + key
                    + " must use canonical unit " + expectedUnit + ", got " + actualUnit);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Photoelectric quantity must be finite: " + key);
        }
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFiniteResults(double photonEnergy, double maximumKineticEnergy,
                                             double stoppingPotential, double wavelength,
                                             double emissionOccurs) {
        double[] values = {photonEnergy, maximumKineticEnergy, stoppingPotential, wavelength, emissionOccurs};
        String[] keys = {"photonEnergy", "maximumKineticEnergy", "stoppingPotential", "wavelength", "emissionOccurs"};
        for (int index = 0; index < values.length; index++) {
            if (!Double.isFinite(values[index])) {
                throw new ArithmeticException("Photoelectric result is not finite: " + keys[index]);
            }
        }
    }

    /** SI inputs after schema canonicalization and unit normalization. */
    public record Parameters(double photonFrequency, double workFunction, double electronCharge) {
        public Parameters {
            if (!Double.isFinite(photonFrequency) || photonFrequency <= 0.0
                    || !Double.isFinite(workFunction) || workFunction < 0.0
                    || !Double.isFinite(electronCharge) || electronCharge <= 0.0) {
                throw new IllegalArgumentException("Photoelectric inputs require positive frequency and charge, "
                        + "and a finite non-negative work function");
            }
        }
    }
}
