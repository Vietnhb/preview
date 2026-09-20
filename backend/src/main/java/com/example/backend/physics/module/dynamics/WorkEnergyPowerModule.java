package com.example.backend.physics.module.dynamics;

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

/** Typed constant-force work-energy model with an independently factored oracle. */
public final class WorkEnergyPowerModule implements PhysicsModule<WorkEnergyPowerModule.Parameters> {
    public static final String MODULE_ID = "work_energy_power";
    public static final String NUMERICAL_SOLVER_ID = "work_energy_power_solver";
    public static final String REFERENCE_SOLVER_ID = "work_energy_power_reference";

    private static final String[] OUTPUT_KEYS = {
            "workByForce", "initialKineticEnergy", "finalKineticEnergy",
            "deltaKineticEnergy", "averagePower"
    };

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double mass = requireCanonical(quantities, "mass", "kg");
        double initialSpeed = requireCanonical(quantities, "initial_speed", "m/s");
        double finalSpeed = requireCanonical(quantities, "final_speed", "m/s");
        double force = requireCanonical(quantities, "force", "N");
        double displacement = requireCanonical(quantities, "displacement", "m");
        double forceAngle = requireCanonical(quantities, "force_angle", "rad");
        double duration = requireCanonical(quantities, "duration", "s");
        return new Parameters(mass, initialSpeed, finalSpeed, force, displacement, forceAngle, duration);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();

        double work = parameters.force() * parameters.displacement()
                * Math.cos(parameters.forceAngle());
        double initialEnergy = 0.5 * parameters.mass()
                * parameters.initialSpeed() * parameters.initialSpeed();
        double finalEnergy = 0.5 * parameters.mass()
                * parameters.finalSpeed() * parameters.finalSpeed();
        double deltaEnergy = finalEnergy - initialEnergy;
        double averagePower = work / parameters.duration();
        requireFiniteResults(work, initialEnergy, finalEnergy, deltaEnergy, averagePower);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("workByForce", repeated(work, time.size()));
        values.put("initialKineticEnergy", repeated(initialEnergy, time.size()));
        values.put("finalKineticEnergy", repeated(finalEnergy, time.size()));
        values.put("deltaKineticEnergy", repeated(deltaEnergy, time.size()));
        values.put("averagePower", repeated(averagePower, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireCheckpointTime(timeSeconds);

        // Evaluate work through the displacement component along the force, and
        // factor the kinetic-energy difference instead of subtracting two energies.
        double projectedDisplacement = parameters.displacement() * Math.cos(parameters.forceAngle());
        double work = parameters.force() * projectedDisplacement;
        double halfMass = parameters.mass() / 2.0;
        double initialSpeed = parameters.initialSpeed();
        double finalSpeed = parameters.finalSpeed();
        double initialEnergy = halfMass * (initialSpeed * initialSpeed);
        double finalEnergy = halfMass * (finalSpeed * finalSpeed);
        double deltaEnergy = halfMass * (finalSpeed - initialSpeed) * (finalSpeed + initialSpeed);
        double averagePower = work / parameters.duration();
        requireFiniteResults(work, initialEnergy, finalEnergy, deltaEnergy, averagePower);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("workByForce", work);
        values.put("initialKineticEnergy", initialEnergy);
        values.put("finalKineticEnergy", finalEnergy);
        values.put("deltaKineticEnergy", deltaEnergy);
        values.put("averagePower", averagePower);
        return new AnalyticalPoint(values);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        double value = quantities.require(key);
        String actualUnit = quantities.unit(key);
        if (!unit.equals(actualUnit)) {
            throw new IllegalArgumentException("Work-energy quantity " + key
                    + " must use canonical unit " + unit + ", got " + actualUnit);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Work-energy quantity must be finite: " + key);
        }
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFiniteResults(double work, double initialEnergy, double finalEnergy,
                                             double deltaEnergy, double averagePower) {
        double[] results = {work, initialEnergy, finalEnergy, deltaEnergy, averagePower};
        for (int index = 0; index < results.length; index++) {
            if (!Double.isFinite(results[index])) {
                throw new ArithmeticException("Work-energy result is not finite: " + OUTPUT_KEYS[index]);
            }
        }
    }

    private static void requireCheckpointTime(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Work-energy reference time must be finite and non-negative");
        }
    }

    public record Parameters(double mass, double initialSpeed, double finalSpeed,
                             double force, double displacement, double forceAngle,
                             double duration) {
        public Parameters {
            if (!Double.isFinite(mass) || mass <= 0.0
                    || !Double.isFinite(initialSpeed) || initialSpeed < 0.0
                    || !Double.isFinite(finalSpeed) || finalSpeed < 0.0
                    || !Double.isFinite(force) || force < 0.0
                    || !Double.isFinite(displacement) || displacement < 0.0
                    || !Double.isFinite(forceAngle) || forceAngle < 0.0 || forceAngle > Math.PI
                    || !Double.isFinite(duration) || duration <= 0.0) {
                throw new IllegalArgumentException("Work-energy inputs must be finite and in their physical domain");
            }
        }
    }
}
