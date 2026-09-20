package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdditionalModernPhysicsModulesTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void eclipseAngularDiameterAndTotalityMatchGeometricGoldenAndOracle() {
        EclipseGeometryModule module = new EclipseGeometryModule();
        var parameters = module.bind(values(
                Map.of("star_radius", 1.0, "star_distance", 10.0,
                        "occluder_radius", 1.0, "occluder_distance", 5.0,
                        "alignment_angle", 0.01),
                Map.of("star_radius", "m", "star_distance", "m", "occluder_radius", "m",
                        "occluder_distance", "m", "alignment_angle", "rad")));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.2, 0.1));
        double starDiameter = 2.0 * Math.atan(0.1);
        double occluderDiameter = 2.0 * Math.atan(0.2);
        assertEquals(starDiameter, output.values().get("starAngularDiameter").get(0), TOLERANCE);
        assertEquals(occluderDiameter, output.values().get("occluderAngularDiameter").get(0), TOLERANCE);
        assertEquals(occluderDiameter - starDiameter - 0.01,
                output.values().get("alignmentMargin").get(0), TOLERANCE);
        assertEquals(1.0, output.values().get("totality").get(0), 0.0);
        var reference = module.referenceAt(parameters, 0.1).values();
        output.values().forEach((key, series) -> assertEquals(series.get(0), reference.get(key), TOLERANCE, key));
    }

    @Test
    void eclipseAlignmentBoundaryIsTotalButInvalidGeometryIsRejected() {
        EclipseGeometryModule module = new EclipseGeometryModule();
        double exactMargin = 2.0 * Math.atan(0.2) - 2.0 * Math.atan(0.1);
        var boundary = module.bind(values(
                Map.of("star_radius", 1.0, "star_distance", 10.0,
                        "occluder_radius", 1.0, "occluder_distance", 5.0,
                        "alignment_angle", exactMargin),
                Map.of("star_radius", "m", "star_distance", "m", "occluder_radius", "m",
                        "occluder_distance", "m", "alignment_angle", "rad")));
        var output = module.solve(boundary, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, output.values().get("alignmentMargin").get(0), 1.0e-15);
        assertEquals(1.0, output.values().get("totality").get(0), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> new EclipseGeometryModule.Parameters(1.0, 1.0, 0.5, 5.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new EclipseGeometryModule.Parameters(1.0, 5.0, 0.5, 0.5, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new EclipseGeometryModule.Parameters(1.0, 5.0, 0.5, 6.0, Math.PI + 0.01));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(boundary, -0.1));
    }

    @Test
    void bandTransitionMatchesPhotonEnergyGoldenAndIndependentReference() {
        EnergyBandTransitionModule module = new EnergyBandTransitionModule();
        var parameters = module.bind(values(
                Map.of("valence_band_energy", -2.0e-19, "conduction_band_energy", 0.5e-19,
                        "photon_frequency", 5.0e14),
                Map.of("valence_band_energy", "J", "conduction_band_energy", "J",
                        "photon_frequency", "Hz")));
        var output = module.solve(parameters, new SimulationClock(1.0, 0.5));
        double gap = 2.5e-19;
        assertEquals(gap, output.values().get("bandGap").get(0), 1.0e-33);
        assertEquals(6.62607015e-34 * 5.0e14,
                output.values().get("photonEnergy").get(0), 1.0e-33);
        assertEquals(6.62607015e-34 * 299_792_458.0 / gap,
                output.values().get("thresholdWavelength").get(0), 1.0e-20);
        assertEquals(1.0, output.values().get("transitionAllowed").get(0), 0.0);
        var reference = module.referenceAt(parameters, 0.0).values();
        output.values().forEach((key, series) -> assertEquals(series.get(0), reference.get(key),
                Math.abs(series.get(0)) * 1.0e-14 + 1.0e-33, key));
    }

    @Test
    void bandGapThresholdIsInclusiveAndInvalidGapIsRejected() {
        EnergyBandTransitionModule module = new EnergyBandTransitionModule();
        double gap = 1.0e-19;
        double thresholdFrequency = gap / 6.62607015e-34;
        var atThreshold = module.bind(values(
                Map.of("valence_band_energy", 0.0, "conduction_band_energy", gap,
                        "photon_frequency", thresholdFrequency),
                Map.of("valence_band_energy", "J", "conduction_band_energy", "J",
                        "photon_frequency", "Hz")));
        assertEquals(1.0, module.solve(atThreshold, new SimulationClock(0.1, 0.1))
                .values().get("transitionAllowed").get(0), 0.0);

        var below = module.bind(values(
                Map.of("valence_band_energy", 0.0, "conduction_band_energy", gap,
                        "photon_frequency", Math.nextDown(thresholdFrequency)),
                Map.of("valence_band_energy", "J", "conduction_band_energy", "J",
                        "photon_frequency", "Hz")));
        assertEquals(0.0, module.solve(below, new SimulationClock(0.1, 0.1))
                .values().get("transitionAllowed").get(0), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> new EnergyBandTransitionModule.Parameters(1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new EnergyBandTransitionModule.Parameters(0.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(atThreshold, Double.NaN));
    }

    @Test
    void nuclearReactionMassDefectAndReleasedEnergyMatchGoldenAndIndependentReference() {
        NuclearReactionEnergyModule module = new NuclearReactionEnergyModule();
        var parameters = module.bind(nuclearValues(5.0e-27, 4.0e-27, 3.0));
        var output = module.solve(parameters, new SimulationClock(0.1, 0.1));
        assertEquals(1.0e-27, output.values().get("massDefect").get(0), 1.0e-42);
        assertEquals(8.9875517873681764e-11,
                output.values().get("releasedEnergyPerReaction").get(0), 1.0e-25);
        assertEquals(2.696265536210453e-10,
                output.values().get("totalReleasedEnergy").get(0), 1.0e-25);
        var reference = module.referenceAt(parameters, 0.0).values();
        output.values().forEach((key, series) -> assertEquals(series.get(0), reference.get(key),
                Math.abs(series.get(0)) * 1.0e-14 + 1.0e-42, key));
    }

    @Test
    void nuclearEqualMassIsZeroEnergyAndInvalidCountOrMassBalanceIsRejected() {
        NuclearReactionEnergyModule module = new NuclearReactionEnergyModule();
        var equalMass = module.bind(nuclearValues(4.0e-27, 4.0e-27, 1.0));
        var output = module.solve(equalMass, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, output.values().get("massDefect").get(0), 0.0);
        assertEquals(0.0, output.values().get("totalReleasedEnergy").get(0), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(nuclearValues(1.0e-27, 2.0e-27, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(nuclearValues(2.0e-27, 1.0e-27, 1.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(nuclearValues(2.0e-27, 1.0e-27, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(equalMass, Double.POSITIVE_INFINITY));
    }

    private static CanonicalQuantityBag nuclearValues(double reactants, double products, double count) {
        return values(Map.of("reactant_mass", reactants, "product_mass", products, "reaction_count", count),
                Map.of("reactant_mass", "kg", "product_mass", "kg", "reaction_count", "1"));
    }

    private static CanonicalQuantityBag values(Map<String, Double> values, Map<String, String> units) {
        Map<String, BigDecimal> decimals = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> decimals.put(key, BigDecimal.valueOf(value)));
        return new CanonicalQuantityBag(decimals, units);
    }
}
