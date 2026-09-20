package com.example.backend.physics.module.practical;

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

/** Typed renewable/fossil energy accounting and emissions model. */
public final class EnergyEnvironmentModule implements PhysicsModule<EnergyEnvironmentModule.Parameters> {
    public static final String MODULE_ID = "energy_environment";
    public static final String NUMERICAL_SOLVER_ID = "energy_environment_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "energy_environment_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "energy_demand", "J"),
                requireCanonical(quantities, "renewable_fraction", "1"),
                requireCanonical(quantities, "fossil_emission_factor", "kg/J"),
                requireCanonical(quantities, "renewable_emission_factor", "kg/J"),
                requireCanonical(quantities, "conversion_efficiency", "1"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double renewableEnergy = parameters.energyDemand() * parameters.renewableFraction();
        double fossilEnergy = parameters.energyDemand() * (1.0 - parameters.renewableFraction());
        double emissions = renewableEnergy * parameters.renewableEmissionFactor()
                + fossilEnergy * parameters.fossilEmissionFactor();
        double usefulEnergy = parameters.energyDemand() * parameters.conversionEfficiency();
        double avoidedEmissions = parameters.energyDemand() * parameters.fossilEmissionFactor() - emissions;
        requireFinite("renewableEnergy", renewableEnergy);
        requireFinite("fossilEnergy", fossilEnergy);
        requireFinite("emissions", emissions);
        requireFinite("usefulEnergy", usefulEnergy);
        requireFinite("avoidedEmissionsVsFossil", avoidedEmissions);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("renewableEnergy", repeated(renewableEnergy, time.size()));
        values.put("fossilEnergy", repeated(fossilEnergy, time.size()));
        values.put("emissions", repeated(emissions, time.size()));
        values.put("usefulEnergy", repeated(usefulEnergy, time.size()));
        values.put("avoidedEmissionsVsFossil", repeated(avoidedEmissions, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Energy-environment reference time must be finite and non-negative");
        }
        double fossilShare = 1.0 - parameters.renewableFraction();
        double fossilEnergy = parameters.energyDemand() * fossilShare;
        double renewableEnergy = parameters.energyDemand() - fossilEnergy;
        double blendedEmissionFactor = parameters.fossilEmissionFactor() * fossilShare
                + parameters.renewableEmissionFactor() * parameters.renewableFraction();
        double emissions = parameters.energyDemand() * blendedEmissionFactor;
        double usefulEnergy = parameters.energyDemand() * parameters.conversionEfficiency();
        double avoidedEmissions = parameters.energyDemand() * parameters.renewableFraction()
                * (parameters.fossilEmissionFactor() - parameters.renewableEmissionFactor());
        requireFinite("reference renewableEnergy", renewableEnergy);
        requireFinite("reference fossilEnergy", fossilEnergy);
        requireFinite("reference emissions", emissions);
        requireFinite("reference usefulEnergy", usefulEnergy);
        requireFinite("reference avoidedEmissionsVsFossil", avoidedEmissions);
        return new AnalyticalPoint(Map.of("renewableEnergy", renewableEnergy,
                "fossilEnergy", fossilEnergy, "emissions", emissions,
                "usefulEnergy", usefulEnergy, "avoidedEmissionsVsFossil", avoidedEmissions));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical energy-environment quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Energy-environment quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Energy-environment output must be finite: " + key);
    }

    /** Demand mix, emission factors and efficiency after canonical unit binding. */
    public record Parameters(double energyDemand, double renewableFraction,
                             double fossilEmissionFactor, double renewableEmissionFactor,
                             double conversionEfficiency) {
        public Parameters {
            if (!Double.isFinite(energyDemand) || energyDemand < 0.0
                    || !Double.isFinite(renewableFraction) || renewableFraction < 0.0 || renewableFraction > 1.0
                    || !Double.isFinite(fossilEmissionFactor) || fossilEmissionFactor < 0.0
                    || !Double.isFinite(renewableEmissionFactor) || renewableEmissionFactor < 0.0
                    || !Double.isFinite(conversionEfficiency) || conversionEfficiency <= 0.0
                    || conversionEfficiency > 1.0) {
                throw new IllegalArgumentException("Energy mix, emission factors and efficiency are outside the physical domain");
            }
        }
    }
}
