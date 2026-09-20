package com.example.backend.physics.module.practical;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PracticalTypedModulesTest {
    @Test
    void energyMixMatchesHandCalculatedEnergyAndEmissions() {
        EnergyEnvironmentModule module = new EnergyEnvironmentModule();
        var parameters = module.bind(values(
                Map.of("energy_demand", 1000.0, "renewable_fraction", 0.4,
                        "fossil_emission_factor", 2.0e-7, "renewable_emission_factor", 5.0e-8,
                        "conversion_efficiency", 0.8),
                Map.of("energy_demand", "J", "renewable_fraction", "1",
                        "fossil_emission_factor", "kg/J", "renewable_emission_factor", "kg/J",
                        "conversion_efficiency", "1")));
        var output = module.solve(parameters, new SimulationClock(0.2, 0.1));
        assertEquals(400.0, output.values().get("renewableEnergy").get(0), 0.0);
        assertEquals(600.0, output.values().get("fossilEnergy").get(0), 0.0);
        assertEquals(1.4e-4, output.values().get("emissions").get(0), 1.0e-18);
        assertEquals(800.0, output.values().get("usefulEnergy").get(0), 0.0);
        assertEquals(6.0e-5, output.values().get("avoidedEmissionsVsFossil").get(0), 1.0e-18);
        var reference = module.referenceAt(parameters, 0.1).values();
        output.values().forEach((key, series) -> assertEquals(series.get(0), reference.get(key),
                Math.abs(series.get(0)) * 1.0e-14 + 1.0e-18, key));
    }

    @Test
    void zeroDemandAndAllFossilOrRenewableMixAreValidBoundaries() {
        EnergyEnvironmentModule module = new EnergyEnvironmentModule();
        var noDemand = module.bind(energyValues(0.0, 0.25, 1.0e-7, 0.0, 1.0));
        module.solve(noDemand, new SimulationClock(0.1, 0.1)).values().values().forEach(
                series -> assertEquals(0.0, series.get(0), 0.0));
        var allFossil = module.bind(energyValues(100.0, 0.0, 2.0e-7, 1.0e-7, 0.5));
        var fossilOutput = module.solve(allFossil, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, fossilOutput.values().get("renewableEnergy").get(0), 0.0);
        assertEquals(100.0, fossilOutput.values().get("fossilEnergy").get(0), 0.0);
        assertEquals(0.0, fossilOutput.values().get("avoidedEmissionsVsFossil").get(0), 0.0);
        var allRenewable = module.bind(energyValues(100.0, 1.0, 2.0e-7, 1.0e-7, 1.0));
        assertEquals(1.0e-5, module.solve(allRenewable, new SimulationClock(0.1, 0.1))
                .values().get("emissions").get(0), 1.0e-18);
    }

    @Test
    void energyEnvironmentRejectsInvalidFractionsEfficiencyUnitsAndFactors() {
        assertThrows(IllegalArgumentException.class,
                () -> new EnergyEnvironmentModule.Parameters(1.0, -0.1, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new EnergyEnvironmentModule.Parameters(1.0, 0.5, 1.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new EnergyEnvironmentModule.Parameters(1.0, 0.5, 1.0, 1.0, 1.01));
        var wrongUnits = values(Map.of("energy_demand", 10.0, "renewable_fraction", 0.5,
                        "fossil_emission_factor", 1.0, "renewable_emission_factor", 1.0,
                        "conversion_efficiency", 1.0),
                Map.of("energy_demand", "J", "renewable_fraction", "1",
                        "fossil_emission_factor", "J/kg", "renewable_emission_factor", "kg/J",
                        "conversion_efficiency", "1"));
        assertThrows(IllegalArgumentException.class, () -> new EnergyEnvironmentModule().bind(wrongUnits));
        assertThrows(IllegalArgumentException.class,
                () -> new EnergyEnvironmentModule.Parameters(1.0, 0.5, -1.0, 0.0, 1.0));
    }

    @Test
    void experimentalGraphSamplesScalarLineAndIndependentEndpointInterpolation() {
        ExperimentalDataGraphModule module = new ExperimentalDataGraphModule();
        var parameters = module.bind(graphValues(-1.0, 3.0, 2.0, 1.0, 5.0));
        var output = module.solve(parameters, new SimulationClock(1.0, 0.1));
        assertEquals(java.util.List.of(-1.0, 0.0, 1.0, 2.0, 3.0), output.time());
        assertEquals(java.util.List.of(-1.0, 1.0, 3.0, 5.0, 7.0), output.values().get("y"));
        assertEquals(java.util.List.of(-1.0, 0.0, 1.0, 2.0, 3.0), output.values().get("x"));
        assertEquals(5.0, module.referenceAt(parameters, 2.0).values().get("y"), 0.0);
        assertEquals(7.0, module.referenceAt(parameters, 10.0).values().get("y"), 0.0);
        assertEquals(-1.0, module.referenceAt(parameters, -10.0).values().get("x"), 0.0);
    }

    @Test
    void experimentalGraphAcceptsTwoSampleBoundaryAndRejectsMalformedDomains() {
        ExperimentalDataGraphModule module = new ExperimentalDataGraphModule();
        var twoSamples = module.bind(graphValues(0.0, 1.0, -2.0, 3.0, 2.0));
        var output = module.solve(twoSamples, new SimulationClock(1.0, 0.1));
        assertEquals(java.util.List.of(3.0, 1.0), output.values().get("y"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentalDataGraphModule.Parameters(1.0, 1.0, 1.0, 0.0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentalDataGraphModule.Parameters(0.0, 1.0, Double.NaN, 0.0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(graphValues(0.0, 1.0, 1.0, 0.0, 1.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(graphValues(0.0, 1.0, 1.0, 0.0, 4097.0)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(twoSamples, Double.NaN));
    }

    private static CanonicalQuantityBag energyValues(double demand, double renewable,
            double fossilFactor, double renewableFactor, double efficiency) {
        return values(Map.of("energy_demand", demand, "renewable_fraction", renewable,
                        "fossil_emission_factor", fossilFactor,
                        "renewable_emission_factor", renewableFactor, "conversion_efficiency", efficiency),
                Map.of("energy_demand", "J", "renewable_fraction", "1",
                        "fossil_emission_factor", "kg/J", "renewable_emission_factor", "kg/J",
                        "conversion_efficiency", "1"));
    }

    private static CanonicalQuantityBag graphValues(double start, double end, double slope,
                                                     double intercept, double count) {
        return values(Map.of("x_start", start, "x_end", end, "slope", slope,
                        "intercept", intercept, "sample_count", count),
                Map.of("x_start", "1", "x_end", "1", "slope", "1",
                        "intercept", "1", "sample_count", "1"));
    }

    private static CanonicalQuantityBag values(Map<String, Double> values, Map<String, String> units) {
        Map<String, BigDecimal> decimals = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> decimals.put(key, BigDecimal.valueOf(value)));
        return new CanonicalQuantityBag(decimals, units);
    }
}
