package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed non-relativistic matter-wave wavelength and Bragg diffraction model. */
public final class DeBroglieDiffractionModule implements PhysicsModule<DeBroglieDiffractionModule.Parameters> {
    public static final String MODULE_ID = "de_broglie_diffraction";
    public static final String NUMERICAL_SOLVER_ID = "de_broglie_diffraction_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "de_broglie_diffraction_reference_v2";
    private static final String MOMENTUM = "momentum";
    private static final String WAVELENGTH = "wavelength";
    private static final String KINETIC_ENERGY = "kineticEnergy";
    private static final String DIFFRACTION_ANGLE = "diffractionAngle";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double rawOrder = requireCanonical(quantities, "diffraction_order", "1");
        if (rawOrder != Math.rint(rawOrder) || rawOrder > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Diffraction order must be a positive integer");
        }
        return new Parameters(requireCanonical(quantities, "particle_mass", "kg"),
                requireCanonical(quantities, "particle_speed", "m/s"),
                requireCanonical(quantities, "lattice_spacing", "m"), (int) rawOrder);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        double momentum = parameters.particleMass() * parameters.particleSpeed();
        double wavelength = PhysicalConstants.PLANCK / momentum;
        double kineticEnergy = 0.5 * momentum * parameters.particleSpeed();
        double sineArgument = parameters.diffractionOrder() * wavelength / parameters.latticeSpacing();
        requirePositiveFinite(MOMENTUM, momentum);
        requirePositiveFinite(WAVELENGTH, wavelength);
        requireFinite(KINETIC_ENERGY, kineticEnergy);
        requireFinite("diffraction sine", sineArgument);
        boolean allowed = sineArgument <= 1.0;
        double diffractionAngle = allowed ? Math.asin(sineArgument) : 0.0;
        requireFinite(DIFFRACTION_ANGLE, diffractionAngle);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(MOMENTUM, List.of(momentum));
        values.put(WAVELENGTH, List.of(wavelength));
        values.put(KINETIC_ENERGY, List.of(kineticEnergy));
        values.put(DIFFRACTION_ANGLE, List.of(diffractionAngle));
        values.put("diffractionAllowed", List.of(allowed ? 1.0 : 0.0));
        return new SolverOutput(List.of(0.0), Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Matter-wave reference time must be finite and non-negative");
        }
        // Independent route through non-relativistic kinetic energy and momentum.
        double kineticEnergy = 0.5 * parameters.particleMass()
                * parameters.particleSpeed() * parameters.particleSpeed();
        double momentum = Math.sqrt(2.0 * parameters.particleMass() * kineticEnergy);
        double wavelength = PhysicalConstants.PLANCK / momentum;
        double sineArgument = (parameters.diffractionOrder() / parameters.latticeSpacing()) * wavelength;
        requirePositiveFinite("reference " + MOMENTUM, momentum);
        requirePositiveFinite("reference " + WAVELENGTH, wavelength);
        requireFinite("reference " + KINETIC_ENERGY, kineticEnergy);
        requireFinite("reference diffraction sine", sineArgument);
        boolean allowed = parameters.diffractionOrder() <= parameters.latticeSpacing() / wavelength;
        double diffractionAngle = allowed ? Math.acos(Math.sqrt(Math.max(0.0, 1.0 - sineArgument * sineArgument))) : 0.0;
        requireFinite("reference " + DIFFRACTION_ANGLE, diffractionAngle);
        return new AnalyticalPoint(Map.of(MOMENTUM, momentum, WAVELENGTH, wavelength,
                KINETIC_ENERGY, kineticEnergy, DIFFRACTION_ANGLE, diffractionAngle,
                "diffractionAllowed", allowed ? 1.0 : 0.0));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical matter-wave quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Matter-wave quantity must be finite: " + key);
        return value;
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Matter-wave output must be finite: " + key);
    }

    private static void requirePositiveFinite(String key, double value) {
        requireFinite(key, value);
        if (value <= 0.0) throw new ArithmeticException("Matter-wave output must be positive: " + key);
    }

    /** Non-relativistic primitive inputs after canonical SI unit binding. */
    public record Parameters(double particleMass, double particleSpeed,
                             double latticeSpacing, int diffractionOrder) {
        public Parameters {
            if (!Double.isFinite(particleMass) || particleMass <= 0.0
                    || !Double.isFinite(particleSpeed) || particleSpeed <= 0.0
                    || particleSpeed >= PhysicalConstants.SPEED_OF_LIGHT
                    || !Double.isFinite(latticeSpacing) || latticeSpacing <= 0.0
                    || diffractionOrder <= 0) {
                throw new IllegalArgumentException("Matter-wave inputs require positive finite mass, sub-light speed, spacing and integer order");
            }
        }
    }
}
