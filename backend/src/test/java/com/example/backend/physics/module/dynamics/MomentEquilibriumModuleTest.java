package com.example.backend.physics.module.dynamics;

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

class MomentEquilibriumModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final MomentEquilibriumModule module = new MomentEquilibriumModule();

    @Test
    void typedBindingMatchesHandCalculatedMomentsAndIndependentCrossProductReference() {
        assertEquals("moment_equilibrium", module.moduleId());
        assertEquals("moment_equilibrium_solver_v2", module.numericalSolverId());
        assertEquals("moment_equilibrium_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                MomentEquilibriumModule.NUMERICAL_SOLVER_ID,
                MomentEquilibriumModule.REFERENCE_SOLVER_ID,
                quantities(10.0, 0.5, Math.PI / 2.0, 4.0, 1.0, -Math.PI / 2.0));

        SolverOutput output = bound.solve(new SimulationClock(4.0, 0.25));
        assertEquals(List.of(0.0), output.time());
        assertEquals(Set.of("moment1", "moment2", "netMoment", "equilibriumResidual"),
                output.values().keySet());
        assertEquals(List.of(5.0), output.values().get("moment1"));
        assertEquals(List.of(-4.0), output.values().get("moment2"));
        assertEquals(List.of(1.0), output.values().get("netMoment"));
        assertEquals(List.of(1.0), output.values().get("equilibriumResidual"));

        Map<String, Double> oracle = bound.reference(0.5).values();
        output.values().forEach((key, series) -> assertEquals(series.getFirst(), oracle.get(key), TOLERANCE, key));
    }

    @Test
    void obliqueForceGoldenDetectsSineCosineAndTorqueSignErrorsInTheReference() {
        var parameters = module.bind(quantities(2.0, 1.0, Math.PI / 6.0,
                8.0, 1.0, -Math.PI / 3.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.1, 0.1));
        Map<String, Double> reference = module.referenceAt(parameters, 0.0).values();

        // For r=(r,0), the y-components are +1 N and -4*sqrt(3) N.
        // Therefore r x F gives +1 N*m and -4*sqrt(3) N*m respectively.
        assertEquals(1.0, output.values().get("moment1").getFirst(), TOLERANCE);
        assertEquals(-4.0 * Math.sqrt(3.0), output.values().get("moment2").getFirst(), TOLERANCE);
        assertEquals(1.0 - 4.0 * Math.sqrt(3.0), output.values().get("netMoment").getFirst(), TOLERANCE);
        assertEquals(4.0 * Math.sqrt(3.0) - 1.0,
                output.values().get("equilibriumResidual").getFirst(), TOLERANCE);
        assertEquals(output.values().get("moment1").getFirst(), reference.get("moment1"), TOLERANCE);
        assertEquals(output.values().get("moment2").getFirst(), reference.get("moment2"), TOLERANCE);
        assertEquals(output.values().get("netMoment").getFirst(), reference.get("netMoment"), TOLERANCE);
        assertEquals(output.values().get("equilibriumResidual").getFirst(),
                reference.get("equilibriumResidual"), TOLERANCE);
    }

    @Test
    void opposingTorquesBalanceAndZeroForcesOrArmsAreValidBoundaries() {
        SolverOutput balanced = module.solve(module.bind(
                quantities(10.0, 0.5, Math.PI / 2.0, 5.0, 1.0, -Math.PI / 2.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0, balanced.values().get("netMoment").getFirst(), TOLERANCE);
        assertEquals(0.0, balanced.values().get("equilibriumResidual").getFirst(), TOLERANCE);

        SolverOutput zeroLever = module.solve(module.bind(quantities(10.0, 0.0, 1.2,
                0.0, 2.0, -0.7)), new SimulationClock(0.1, 0.1));
        assertEquals(0.0, zeroLever.values().get("moment1").getFirst(), TOLERANCE);
        assertEquals(0.0, zeroLever.values().get("moment2").getFirst(), TOLERANCE);
        assertEquals(0.0, zeroLever.values().get("equilibriumResidual").getFirst(), TOLERANCE);
    }

    @Test
    void acceptsZeroAnglesAndRejectsInvalidDomainsUnitsAndReferenceTimes() {
        SolverOutput parallelForce = module.solve(module.bind(
                quantities(10.0, 0.5, 0.0, 4.0, 1.0, Math.PI)), new SimulationClock(0.1, 0.1));
        assertEquals(0.0, parallelForce.values().get("moment1").getFirst(), TOLERANCE);

        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 0.5, 1.0, 4.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, -0.5, 1.0, 4.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new MomentEquilibriumModule.Parameters(1.0, 1.0, Double.NaN, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantitiesWithForceUnit("kN")));

        var valid = module.bind(quantities(1.0, 1.0, 0.0, 1.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsMomentProductOrNetMomentOverflow() {
        var productOverflow = module.bind(quantities(Double.MAX_VALUE, 2.0, Math.PI / 2.0,
                0.0, 0.0, 0.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(productOverflow, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(productOverflow, 0.0));

        var netOverflow = module.bind(quantities(1.0e308, 1.0, Math.PI / 2.0,
                1.0e308, 1.0, Math.PI / 2.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(netOverflow, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(netOverflow, 0.0));
    }

    private static CanonicalQuantityBag quantities(double force1, double arm1, double angle1,
                                                   double force2, double arm2, double angle2) {
        return new CanonicalQuantityBag(
                Map.of("force_1", decimal(force1), "arm_1", decimal(arm1), "angle_1", decimal(angle1),
                        "force_2", decimal(force2), "arm_2", decimal(arm2), "angle_2", decimal(angle2)),
                Map.of("force_1", "N", "arm_1", "m", "angle_1", "rad",
                        "force_2", "N", "arm_2", "m", "angle_2", "rad"));
    }

    private static CanonicalQuantityBag quantitiesWithForceUnit(String forceUnit) {
        return new CanonicalQuantityBag(
                Map.of("force_1", decimal(1.0), "arm_1", decimal(1.0), "angle_1", decimal(0.0),
                        "force_2", decimal(1.0), "arm_2", decimal(1.0), "angle_2", decimal(0.0)),
                Map.of("force_1", forceUnit, "arm_1", "m", "angle_1", "rad",
                        "force_2", "N", "arm_2", "m", "angle_2", "rad"));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
