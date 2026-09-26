package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed mass-balance model for energy released by repeated nuclear reactions. */
public final class NuclearReactionEnergyModule
        implements PhysicsModule<NuclearReactionEnergyModule.Parameters> {
    public static final String MODULE_ID = "nuclear_reaction_energy";
    public static final String NUMERICAL_SOLVER_ID = "nuclear_reaction_energy_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "nuclear_reaction_energy_reference_v2";
    private static final String MASS_DEFECT = "massDefect";
    private static final String RELEASED_ENERGY_PER_REACTION = "releasedEnergyPerReaction";
    private static final String TOTAL_RELEASED_ENERGY = "totalReleasedEnergy";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "reactant_mass", "kg");
        requireUnit(quantities, "product_mass", "kg");
        requireUnit(quantities, "reaction_count", "1");
        return new Parameters(quantities.require("reactant_mass"),
                quantities.require("product_mass"), quantities.require("reaction_count"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double massDefect = parameters.reactantMass() - parameters.productMass();
        double energyPerReaction = massDefect * PhysicalConstants.SPEED_OF_LIGHT
                * PhysicalConstants.SPEED_OF_LIGHT;
        double totalEnergy = energyPerReaction * parameters.reactionCount();
        requireFinite(MASS_DEFECT, massDefect);
        requireFinite(RELEASED_ENERGY_PER_REACTION, energyPerReaction);
        requireFinite(TOTAL_RELEASED_ENERGY, totalEnergy);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(MASS_DEFECT, repeated(massDefect, time.size()));
        values.put(RELEASED_ENERGY_PER_REACTION, repeated(energyPerReaction, time.size()));
        values.put(TOTAL_RELEASED_ENERGY, repeated(totalEnergy, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Nuclear-reaction reference time must be finite and non-negative");
        }
        BigDecimal reactants = BigDecimal.valueOf(parameters.reactantMass());
        BigDecimal products = BigDecimal.valueOf(parameters.productMass());
        BigDecimal defect = reactants.subtract(products);
        BigDecimal lightSpeed = BigDecimal.valueOf(PhysicalConstants.SPEED_OF_LIGHT);
        BigDecimal energyPerReaction = defect.multiply(lightSpeed).multiply(lightSpeed);
        BigDecimal totalEnergy = energyPerReaction.multiply(BigDecimal.valueOf(parameters.reactionCount()));
        double massDefect = defect.doubleValue();
        double perReaction = energyPerReaction.doubleValue();
        double released = totalEnergy.doubleValue();
        requireFinite("reference " + MASS_DEFECT, massDefect);
        requireFinite("reference " + RELEASED_ENERGY_PER_REACTION, perReaction);
        requireFinite("reference " + TOTAL_RELEASED_ENERGY, released);
        return new AnalyticalPoint(Map.of(MASS_DEFECT, massDefect,
                RELEASED_ENERGY_PER_REACTION, perReaction, TOTAL_RELEASED_ENERGY, released));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical nuclear-reaction quantity " + key
                    + " must use unit " + unit);
        }
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Nuclear-reaction output must be finite: " + key);
    }

    /** Canonical masses and a positive integer reaction count. */
    public record Parameters(double reactantMass, double productMass, double reactionCount) {
        public Parameters {
            if (!Double.isFinite(reactantMass) || reactantMass <= 0.0
                    || !Double.isFinite(productMass) || productMass <= 0.0
                    || productMass > reactantMass
                    || !Double.isFinite(reactionCount) || reactionCount <= 0.0
                    || reactionCount != Math.rint(reactionCount)) {
                throw new IllegalArgumentException("Nuclear-reaction masses and count are outside the physical domain");
            }
        }
    }
}
