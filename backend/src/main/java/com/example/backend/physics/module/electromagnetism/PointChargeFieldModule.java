package com.example.backend.physics.module.electromagnetism;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed electric field and potential of an isolated point charge. */
public final class PointChargeFieldModule implements PhysicsModule<PointChargeFieldModule.Parameters> {
    public static final String MODULE_ID = "point_charge_field";
    public static final String NUMERICAL_SOLVER_ID = "point_charge_field_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "point_charge_field_reference_v2";
    private static final double COULOMB_CONSTANT = 8.9875517923e9;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "charge", "C"),
                requireCanonical(quantities, "distance", "m"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();

        // Apply Coulomb's law to a one-coulomb positive test charge at the
        // radial probe. The field output is the force magnitude per test charge.
        double field = COULOMB_CONSTANT * Math.abs(parameters.charge())
                / (parameters.distance() * parameters.distance());
        double potential = COULOMB_CONSTANT * parameters.charge() / parameters.distance();
        requireFinite(field, "electricField");
        requireFinite(potential, "electricPotential");

        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("electricField", field, "electricPotential", potential));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Point-charge reference time must be finite and non-negative");
        }

        // Independent electrostatic-potential formulation. The potential is
        // evaluated from its radial integral, then the field from -dV/dr.
        // Log-domain evaluation avoids the numerical solver's intermediate
        // multiplication/division ordering.
        double field;
        double potential;
        if (parameters.charge() == 0.0) {
            field = 0.0;
            potential = 0.0;
        } else {
            double logConstant = Math.log(COULOMB_CONSTANT);
            double logMagnitudeCharge = Math.log(Math.abs(parameters.charge()));
            double logDistance = Math.log(parameters.distance());
            potential = Math.copySign(Math.exp(logConstant + logMagnitudeCharge - logDistance),
                    parameters.charge());
            field = Math.exp(logConstant + logMagnitudeCharge - 2.0 * logDistance);
        }
        requireFinite(field, "reference electricField");
        requireFinite(potential, "reference electricPotential");
        return new AnalyticalPoint(Map.of("electricField", field, "electricPotential", potential));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        String actualUnit = quantities.unit(key);
        if (!expectedUnit.equals(actualUnit)) {
            throw new IllegalArgumentException("Canonical point-charge quantity " + key
                    + " must use unit " + expectedUnit + ", got " + actualUnit);
        }
        return quantities.require(key);
    }

    private static void requireFinite(double value, String outputKey) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Point-charge result is not finite: " + outputKey);
        }
    }

    /** SI-valued inputs after schema-boundary canonicalization. */
    public record Parameters(double charge, double distance) {
        public Parameters {
            if (!Double.isFinite(charge) || !Double.isFinite(distance) || distance <= 0.0) {
                throw new IllegalArgumentException("Point charge must be finite and probe distance finite and positive");
            }
        }
    }
}
