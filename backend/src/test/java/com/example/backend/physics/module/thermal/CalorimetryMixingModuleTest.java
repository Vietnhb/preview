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

class CalorimetryMixingModuleTest {
    private static final double TOLERANCE = 1.0e-9;
    private final CalorimetryMixingModule module = new CalorimetryMixingModule();

    @Test
    void registeredBindingMatchesIndependentEnergyConservationGolden() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                CalorimetryMixingModule.NUMERICAL_SOLVER_ID,
                CalorimetryMixingModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 900.0, 300.0, 1.0, 4200.0, 400.0));

        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(Set.of("temperature1", "temperature2", "equilibriumTemperature", "heat1", "heat2"),
                output.values().keySet());
        assertEquals(List.of(370.0, 370.0, 370.0), output.values().get("temperature1"));
        assertEquals(List.of(370.0, 370.0, 370.0), output.values().get("temperature2"));
        assertEquals(List.of(370.0, 370.0, 370.0), output.values().get("equilibriumTemperature"));
        assertEquals(List.of(126_000.0, 126_000.0, 126_000.0), output.values().get("heat1"));
        assertEquals(List.of(-126_000.0, -126_000.0, -126_000.0), output.values().get("heat2"));

        var oracle = bound.reference(0.1).values();
        assertEquals(5, oracle.size());
        assertEquals(370.0, oracle.get("equilibriumTemperature"), TOLERANCE);
        assertEquals(126_000.0, oracle.get("heat1"), TOLERANCE);
        assertEquals(-126_000.0, oracle.get("heat2"), TOLERANCE);
        assertEquals(0.0, oracle.get("heat1") + oracle.get("heat2"), TOLERANCE);
    }

    @Test
    void equalTemperaturesAreAnEquilibriumBoundaryWithNoHeatTransfer() {
        SolverOutput output = module.solve(module.bind(quantities(0.5, 800.0, 350.0,
                4.0, 100.0, 350.0)), new SimulationClock(0.1, 0.1));
        assertEquals(350.0, output.values().get("equilibriumTemperature").get(0), TOLERANCE);
        assertEquals(0.0, output.values().get("heat1").get(0), TOLERANCE);
        assertEquals(0.0, output.values().get("heat2").get(0), TOLERANCE);
    }

    @Test
    void binderRejectsInvalidDomainsMissingInputsAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(null));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(new CanonicalQuantityBag(Map.of(), Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 900.0, 300.0, 1.0, 4200.0, 400.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 0.0, 300.0, 1.0, 4200.0, 400.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 900.0, 0.0, 1.0, 4200.0, 400.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 900.0, 300.0, 1.0, 4200.0, -1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new CalorimetryMixingModule.Parameters(1.0, Double.NaN, 300.0, 1.0, 1.0, 300.0));

        CanonicalQuantityBag wrongUnit = quantitiesWithUnits("g", "J/(kg*K)", "K", "kg",
                "J/(kg*K)", "K", 2.0, 900.0, 300.0, 1.0, 4200.0, 400.0);
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void nonFiniteDerivedCapacityAndInvalidReferenceTimeAreRejected() {
        var overflow = module.bind(quantities(Double.MAX_VALUE, 2.0, 300.0,
                1.0, 1.0, 400.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(overflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(overflow, 0.0));

        var ordinary = module.bind(quantities(2.0, 900.0, 300.0, 1.0, 4200.0, 400.0));
        assertThrows(IllegalArgumentException.class, () -> module.solve(ordinary, null));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(ordinary, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(ordinary, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(ordinary, Double.POSITIVE_INFINITY));
    }

    private static CanonicalQuantityBag quantities(double mass1, double heatCapacity1, double temperature1,
                                                   double mass2, double heatCapacity2, double temperature2) {
        return quantitiesWithUnits("kg", "J/(kg*K)", "K", "kg", "J/(kg*K)", "K",
                mass1, heatCapacity1, temperature1, mass2, heatCapacity2, temperature2);
    }

    private static CanonicalQuantityBag quantitiesWithUnits(String mass1Unit, String specificHeat1Unit,
                                                             String temperature1Unit, String mass2Unit,
                                                             String specificHeat2Unit, String temperature2Unit,
                                                             double mass1, double specificHeat1, double temperature1,
                                                             double mass2, double specificHeat2, double temperature2) {
        return new CanonicalQuantityBag(
                Map.of("mass_1", bd(mass1), "specific_heat_1", bd(specificHeat1),
                        "initial_temperature_1", bd(temperature1), "mass_2", bd(mass2),
                        "specific_heat_2", bd(specificHeat2), "initial_temperature_2", bd(temperature2)),
                Map.of("mass_1", mass1Unit, "specific_heat_1", specificHeat1Unit,
                        "initial_temperature_1", temperature1Unit, "mass_2", mass2Unit,
                        "specific_heat_2", specificHeat2Unit, "initial_temperature_2", temperature2Unit));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
