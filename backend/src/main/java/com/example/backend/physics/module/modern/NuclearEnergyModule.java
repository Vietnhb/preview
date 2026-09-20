package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed mass-defect conversion for repeated nuclear reactions. */
public final class NuclearEnergyModule implements PhysicsModule<NuclearEnergyModule.Parameters> {
    public static final String MODULE_ID = "nuclear_energy";
    public static final String NUMERICAL_SOLVER_ID = "nuclear_energy_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "nuclear_energy_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "mass_defect", "kg");
        requireUnit(quantities, "reaction_count", "1");
        return new Parameters(quantities.require("mass_defect"), quantities.require("reaction_count"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();

        double energyPerReaction = parameters.massDefect()
                * PhysicalConstants.SPEED_OF_LIGHT * PhysicalConstants.SPEED_OF_LIGHT;
        double releasedEnergy = energyPerReaction * parameters.reactionCount();
        requireFinite("releasedEnergy", releasedEnergy);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("releasedEnergy", Collections.nCopies(time.size(), releasedEnergy));
        values.put("massDefect", Collections.nCopies(time.size(), parameters.massDefect()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Nuclear-energy reference time must be finite and non-negative");
        }

        // The oracle evaluates the exact decimal product before one final rounding
        // to double, independently of the numerical path's double intermediates.
        BigDecimal lightSpeed = BigDecimal.valueOf(PhysicalConstants.SPEED_OF_LIGHT);
        BigDecimal exactEnergy = BigDecimal.valueOf(parameters.massDefect())
                .multiply(BigDecimal.valueOf(parameters.reactionCount()))
                .multiply(lightSpeed)
                .multiply(lightSpeed);
        double releasedEnergy = exactEnergy.doubleValue();
        requireFinite("reference releasedEnergy", releasedEnergy);
        return new AnalyticalPoint(Map.of("releasedEnergy", releasedEnergy,
                "massDefect", parameters.massDefect()));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical nuclear-energy quantity " + key
                    + " must use unit " + unit);
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Nuclear-energy result is not finite: " + outputKey);
        }
    }

    /** Canonical mass and dimensionless reaction count after ingress validation. */
    public record Parameters(double massDefect, double reactionCount) {
        public Parameters {
            if (!Double.isFinite(massDefect) || massDefect < 0.0
                    || !Double.isFinite(reactionCount) || reactionCount <= 0.0
                    || reactionCount != Math.rint(reactionCount)) {
                throw new IllegalArgumentException("Nuclear-energy mass defect must be non-negative and reaction count a positive integer");
            }
        }
    }
}
