package com.example.backend.physics.module.thermal;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhaseChangeModuleTest {
    private static final double TOLERANCE = 1.0e-9;
    private final PhaseChangeModule module = new PhaseChangeModule();

    @Test
    void registeredBindingMatchesIndependentHeatingCurveGoldenAcrossEveryStage() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                PhaseChangeModule.NUMERICAL_SOLVER_ID,
                PhaseChangeModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 250.0, 300.0, 400.0,
                        1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 1000.0));

        SolverOutput output = bound.solve(new SimulationClock(1300.0, 100.0));
        assertEquals(14, output.time().size());
        assertEquals(Set.of("temperature", "heatAdded", "liquidFraction", "vaporFraction"),
                output.values().keySet());
        assertEquals(List.of(250.0, 300.0, 300.0, 300.0, 325.0, 350.0, 375.0,
                        400.0, 400.0, 400.0, 400.0, 400.0, 450.0, 500.0),
                output.values().get("temperature"));
        assertEquals(List.of(0.0, 0.0, 0.5, 1.0, 1.0, 1.0, 1.0,
                        1.0, 0.75, 0.5, 0.25, 0.0, 0.0, 0.0),
                output.values().get("liquidFraction"));
        assertEquals(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                        0.0, 0.25, 0.5, 0.75, 1.0, 1.0, 1.0),
                output.values().get("vaporFraction"));

        // The golden curve uses Q = P t and specific energy thresholds from
        // the three sensible-heating intervals and two latent-heat plateaus.
        assertEquals(100_000.0, output.values().get("heatAdded").get(1), TOLERANCE);
        assertEquals(300.0, output.values().get("temperature").get(1), TOLERANCE);
        assertEquals(300.0, output.values().get("temperature").get(3), TOLERANCE);
        assertEquals(400.0, output.values().get("temperature").get(7), TOLERANCE);
        assertEquals(1_100_000.0, output.values().get("heatAdded").get(11), TOLERANCE);
        for (int index : List.of(8, 9, 10, 11, 12, 13)) {
            assertEquals(1.0, output.values().get("liquidFraction").get(index)
                    + output.values().get("vaporFraction").get(index), TOLERANCE);
        }

        for (int index : List.of(0, 1, 2, 3, 5, 7, 9, 11, 13)) {
            var oracle = bound.reference(output.time().get(index)).values();
            assertEquals(output.values().get("temperature").get(index), oracle.get("temperature"), TOLERANCE);
            assertEquals(output.values().get("heatAdded").get(index), oracle.get("heatAdded"), TOLERANCE);
            assertEquals(output.values().get("liquidFraction").get(index), oracle.get("liquidFraction"), TOLERANCE);
            assertEquals(output.values().get("vaporFraction").get(index), oracle.get("vaporFraction"), TOLERANCE);
        }
    }

    @Test
    void absoluteZeroAndInitialAtMeltingPointAreValidBoundaries() {
        var atAbsoluteZero = module.bind(quantities(1.0, 0.0, 0.0, 100.0,
                1.0, 1.0, 1.0, 10.0, 20.0, 1.0));
        SolverOutput initial = module.solve(atAbsoluteZero, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, initial.values().get("temperature").get(0), TOLERANCE);
        assertEquals(0.0, initial.values().get("liquidFraction").get(0), TOLERANCE);

        var startsAtMelt = module.bind(quantities(1.0, 300.0, 300.0, 400.0,
                1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 1000.0));
        var melting = module.referenceAt(startsAtMelt, 0.0).values();
        assertEquals(300.0, melting.get("temperature"), TOLERANCE);
        assertEquals(0.0, melting.get("liquidFraction"), TOLERANCE);
        assertEquals(0.0, melting.get("vaporFraction"), TOLERANCE);
    }

    @Test
    void binderRejectsMissingValuesWrongUnitsAndInvalidPhaseOrderingOrMaterialValues() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(null));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(new CanonicalQuantityBag(Map.of(), Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, -1.0, 300.0, 400.0,
                        1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 1000.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 250.0, 400.0, 300.0,
                        1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 1000.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 250.0, 300.0, 400.0,
                        0.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 1000.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 250.0, 300.0, 400.0,
                        1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 0.0)));

        CanonicalQuantityBag wrongUnit = quantitiesWithUnits("g", "K", "K", "K",
                "J/(kg*K)", "J/(kg*K)", "J/(kg*K)", "J/kg", "J/kg", "W",
                1.0, 250.0, 300.0, 400.0, 1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 1000.0);
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsInvalidClockReferenceTimesAndUnrepresentableHeatCurve() {
        var ordinary = module.bind(quantities(1.0, 250.0, 300.0, 400.0,
                1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, 1000.0));
        assertThrows(IllegalArgumentException.class, () -> module.solve(ordinary, null));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(ordinary, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(ordinary, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(ordinary, Double.POSITIVE_INFINITY));

        var extremePower = module.bind(quantities(1.0, 250.0, 300.0, 400.0,
                1000.0, 2000.0, 1000.0, 100_000.0, 200_000.0, Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(extremePower, new SimulationClock(2.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(extremePower, 2.0));
    }

    private static CanonicalQuantityBag quantities(double mass, double initialTemperature,
                                                   double meltingTemperature, double boilingTemperature,
                                                   double solidHeat, double liquidHeat, double gasHeat,
                                                   double fusionHeat, double vaporizationHeat, double power) {
        return quantitiesWithUnits("kg", "K", "K", "K", "J/(kg*K)", "J/(kg*K)",
                "J/(kg*K)", "J/kg", "J/kg", "W", mass, initialTemperature,
                meltingTemperature, boilingTemperature, solidHeat, liquidHeat,
                gasHeat, fusionHeat, vaporizationHeat, power);
    }

    private static CanonicalQuantityBag quantitiesWithUnits(String massUnit, String initialTemperatureUnit,
                                                            String meltingTemperatureUnit, String boilingTemperatureUnit,
                                                            String solidHeatUnit, String liquidHeatUnit, String gasHeatUnit,
                                                            String fusionHeatUnit, String vaporizationHeatUnit, String powerUnit,
                                                            double mass, double initialTemperature,
                                                            double meltingTemperature, double boilingTemperature,
                                                            double solidHeat, double liquidHeat, double gasHeat,
                                                            double fusionHeat, double vaporizationHeat, double power) {
        return new CanonicalQuantityBag(
                Map.of("mass", bd(mass), "initial_temperature", bd(initialTemperature),
                        "melting_temperature", bd(meltingTemperature), "boiling_temperature", bd(boilingTemperature),
                        "specific_heat_solid", bd(solidHeat), "specific_heat_liquid", bd(liquidHeat),
                        "specific_heat_gas", bd(gasHeat), "latent_heat_fusion", bd(fusionHeat),
                        "latent_heat_vaporization", bd(vaporizationHeat), "heating_power", bd(power)),
                Map.of("mass", massUnit, "initial_temperature", initialTemperatureUnit,
                        "melting_temperature", meltingTemperatureUnit, "boiling_temperature", boilingTemperatureUnit,
                        "specific_heat_solid", solidHeatUnit, "specific_heat_liquid", liquidHeatUnit,
                        "specific_heat_gas", gasHeatUnit, "latent_heat_fusion", fusionHeatUnit,
                        "latent_heat_vaporization", vaporizationHeatUnit, "heating_power", powerUnit));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
