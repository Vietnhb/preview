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

class TemperatureScalesModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final TemperatureScalesModule module = new TemperatureScalesModule();

    @Test
    void registeredBindingMatchesTemperatureScaleGoldensAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                TemperatureScalesModule.NUMERICAL_SOLVER_ID,
                TemperatureScalesModule.REFERENCE_SOLVER_ID,
                quantities(100.0));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("celsius", "kelvin", "fahrenheit"), output.values().keySet());
        assertEquals(List.of(100.0, 100.0, 100.0), output.values().get("celsius"));
        assertEquals(List.of(373.15, 373.15, 373.15), output.values().get("kelvin"));
        assertEquals(List.of(212.0, 212.0, 212.0), output.values().get("fahrenheit"));

        var oracle = bound.reference(0.5).values();
        assertEquals(100.0, oracle.get("celsius"), TOLERANCE);
        assertEquals(373.15, oracle.get("kelvin"), TOLERANCE);
        assertEquals(212.0, oracle.get("fahrenheit"), TOLERANCE);
        assertEquals(output.values().get("kelvin").get(0), oracle.get("kelvin"), TOLERANCE);
        assertEquals(output.values().get("fahrenheit").get(0), oracle.get("fahrenheit"), TOLERANCE);
    }

    @Test
    void freezingPointAndAbsoluteZeroAreValidBoundaries() {
        SolverOutput freezing = module.solve(module.bind(quantities(0.0)), new SimulationClock(0.1, 0.1));
        assertEquals(273.15, freezing.values().get("kelvin").get(0), TOLERANCE);
        assertEquals(32.0, freezing.values().get("fahrenheit").get(0), TOLERANCE);

        SolverOutput absoluteZero = module.solve(module.bind(quantities(-273.15)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0, absoluteZero.values().get("kelvin").get(0), TOLERANCE);
        assertEquals(-459.67, absoluteZero.values().get("fahrenheit").get(0), TOLERANCE);
        assertEquals(0.0, module.referenceAt(module.bind(quantities(-273.15)), 0.0)
                .values().get("kelvin"), TOLERANCE);
    }

    @Test
    void rejectsMissingValuesWrongUnitsSubAbsoluteZeroAndInvalidReferenceTime() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(null));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(new CanonicalQuantityBag(Map.of(), Map.of())));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-273.150001)));
        assertThrows(IllegalArgumentException.class,
                () -> new TemperatureScalesModule.Parameters(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(new CanonicalQuantityBag(Map.of("temperature_celsius", bd(20.0)),
                        Map.of("temperature_celsius", "C"))));

        var parameters = module.bind(quantities(20.0));
        assertThrows(IllegalArgumentException.class, () -> module.solve(parameters, null));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(parameters, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsConversionOverflowInsteadOfReturningNonFiniteValues() {
        var largeFiniteInput = module.bind(quantities(1.0e308));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(largeFiniteInput, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(largeFiniteInput, 0.0));
    }

    private static CanonicalQuantityBag quantities(double celsius) {
        return new CanonicalQuantityBag(Map.of("temperature_celsius", bd(celsius)),
                Map.of("temperature_celsius", "degC"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
