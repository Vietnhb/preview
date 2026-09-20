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

class FirstLawThermodynamicsModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final FirstLawThermodynamicsModule module = new FirstLawThermodynamicsModule();

    @Test
    void registeredBindingMatchesFirstLawGoldenAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                FirstLawThermodynamicsModule.NUMERICAL_SOLVER_ID,
                FirstLawThermodynamicsModule.REFERENCE_SOLVER_ID,
                quantities(100.0, 50.0, 20.0));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("internalEnergy", "deltaInternalEnergy", "heat", "work"),
                output.values().keySet());
        assertEquals(List.of(130.0, 130.0, 130.0), output.values().get("internalEnergy"));
        assertEquals(List.of(30.0, 30.0, 30.0), output.values().get("deltaInternalEnergy"));
        assertEquals(List.of(50.0, 50.0, 50.0), output.values().get("heat"));
        assertEquals(List.of(20.0, 20.0, 20.0), output.values().get("work"));

        var oracle = bound.reference(0.5).values();
        assertEquals(130.0, oracle.get("internalEnergy"), TOLERANCE);
        assertEquals(30.0, oracle.get("deltaInternalEnergy"), TOLERANCE);
        assertEquals(50.0, oracle.get("heat"), TOLERANCE);
        assertEquals(20.0, oracle.get("work"), TOLERANCE);
    }

    @Test
    void zeroNetEnergyAndNegativeHeatAndWorkFollowTheDeclaredSignConvention() {
        SolverOutput zeroNet = module.solve(module.bind(quantities(250.0, 80.0, 80.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0, zeroNet.values().get("deltaInternalEnergy").get(0), TOLERANCE);
        assertEquals(250.0, zeroNet.values().get("internalEnergy").get(0), TOLERANCE);

        SolverOutput netCooling = module.solve(module.bind(quantities(10.0, -5.0, -2.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(-3.0, netCooling.values().get("deltaInternalEnergy").get(0), TOLERANCE);
        assertEquals(7.0, netCooling.values().get("internalEnergy").get(0), TOLERANCE);
        assertEquals(-5.0, netCooling.values().get("heat").get(0), TOLERANCE);
        assertEquals(-2.0, netCooling.values().get("work").get(0), TOLERANCE);
    }

    @Test
    void binderRequiresEveryEnergyInJoulesAndRejectsNonFiniteOrOverflowingResults() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(null));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(new CanonicalQuantityBag(Map.of(), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> module.bind(
                quantitiesWithUnits("J", "kJ", "J", 100.0, 50.0, 20.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new FirstLawThermodynamicsModule.Parameters(Double.NaN, 0.0, 0.0));

        var overflowing = module.bind(quantities(Double.MAX_VALUE, Double.MAX_VALUE, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(overflowing, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(overflowing, 0.0));
    }

    @Test
    void rejectsMissingClockAndInvalidReferenceTime() {
        var parameters = module.bind(quantities(100.0, 50.0, 20.0));
        assertThrows(IllegalArgumentException.class, () -> module.solve(parameters, null));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(parameters, Double.POSITIVE_INFINITY));
    }

    private static CanonicalQuantityBag quantities(double initialEnergy, double heat, double work) {
        return quantitiesWithUnits("J", "J", "J", initialEnergy, heat, work);
    }

    private static CanonicalQuantityBag quantitiesWithUnits(String initialEnergyUnit, String heatUnit,
                                                             String workUnit, double initialEnergy,
                                                             double heat, double work) {
        return new CanonicalQuantityBag(
                Map.of("initial_internal_energy", bd(initialEnergy), "heat_added", bd(heat), "work_done", bd(work)),
                Map.of("initial_internal_energy", initialEnergyUnit, "heat_added", heatUnit, "work_done", workUnit));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
