package com.example.backend.physics.module.electromagnetism;

import com.example.backend.physics.compatibility.LegacySolverOutputAdapter;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.PhysicsOutputValidator;
import com.example.backend.physics.output.ScalarOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointChargeFieldModuleTest {
    private static final double TOLERANCE = 1.0e-8;
    private static final int MAX_TIMELINE_SAMPLES = 16_385;
    private final PointChargeFieldModule module = new PointChargeFieldModule();

    @Test
    void typedBindingProducesHandCalculatedScalarGoldenOutputs() {
        var parameters = module.bind(quantities(3.0e-6, 0.3));
        SolverOutput output = module.solve(parameters, new SimulationClock(1.0, 0.1));

        assertEquals(299_585.05974333335, output.scalarOutputs().get("electricField"), TOLERANCE);
        assertEquals(89_875.517923, output.scalarOutputs().get("electricPotential"), TOLERANCE);
        assertTrue(output.values().isEmpty(), "static scalar outputs must not be encoded as repeated series");

        var reference = module.referenceAt(parameters, 0.5).values();
        assertEquals(299_585.05974333335, reference.get("electricField"), TOLERANCE);
        assertEquals(89_875.517923, reference.get("electricPotential"), TOLERANCE);
    }

    @Test
    void negativeChargeReversesPotentialButNotFieldMagnitude() {
        var parameters = module.bind(quantities(-2.0e-6, 0.5));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.2, 0.1));

        assertEquals(71_900.4143384, output.scalarOutputs().get("electricField"), TOLERANCE);
        assertEquals(-35_950.2071692, output.scalarOutputs().get("electricPotential"), TOLERANCE);
        var reference = module.referenceAt(parameters, 0.0).values();
        assertEquals(71_900.4143384, reference.get("electricField"), TOLERANCE);
        assertEquals(-35_950.2071692, reference.get("electricPotential"), TOLERANCE);
    }

    @Test
    void scalarOutputContractChecksKindsUnitsAndRequiredKeys() {
        SolverOutput output = module.solve(module.bind(quantities(3.0e-6, 0.3)),
                new SimulationClock(0.2, 0.1));
        var contract = new PhysicsOutputContract("point_charge_field", "1.1", "point_charge_field",
                Map.of(
                        "electricField", new PhysicsOutputContract.OutputDefinition(
                                PhysicsOutput.OutputKind.SCALAR, "N/C", true),
                        "electricPotential", new PhysicsOutputContract.OutputDefinition(
                                PhysicsOutput.OutputKind.SCALAR, "V", true)),
                MAX_TIMELINE_SAMPLES);
        PhysicsOutputFrame frame = LegacySolverOutputAdapter.adapt(output,
                Map.of("electricField", "N/C", "electricPotential", "V"));

        PhysicsOutputValidator.validate(contract, frame);
        assertTrue(frame.outputs().stream().allMatch(value -> value instanceof ScalarOutput));
        assertThrows(IllegalArgumentException.class,
                () -> PhysicsOutputValidator.validate(contract, new PhysicsOutputFrame(frame.timeSeconds(),
                        frame.outputs().stream().filter(value -> !value.key().equals("electricPotential")).toList())));
    }

    @Test
    void zeroChargeAndPositiveDistanceBoundaryAreValid() {
        var parameters = module.bind(quantities(0.0, 100.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.1, 0.1));

        assertEquals(0.0, output.scalarOutputs().get("electricField"));
        assertEquals(0.0, output.scalarOutputs().get("electricPotential"));
        var reference = module.referenceAt(parameters, 0.0).values();
        assertEquals(0.0, reference.get("electricField"));
        assertEquals(0.0, reference.get("electricPotential"));
    }

    @Test
    void rejectsMissingWrongUnitNonFiniteAndNonPositiveDistance() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, -1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(Double.NaN, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new PointChargeFieldModule.Parameters(1.0, Double.POSITIVE_INFINITY));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("charge", BigDecimal.ONE, "distance", BigDecimal.ONE),
                Map.of("charge", "C", "distance", "cm"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsNonFinitePhysicsResultAndInvalidReferenceTime() {
        var tinyDistance = module.bind(quantities(1.0, Double.MIN_VALUE));
        assertThrows(ArithmeticException.class,
                () -> module.solve(tinyDistance, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(module.bind(quantities(1.0, 1.0)), -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(module.bind(quantities(1.0, 1.0)), Double.NaN));
    }

    @Test
    void clockResourceLimitBoundsTimelineWithoutTurningScalarsIntoSeries() {
        SolverOutput output = module.solve(module.bind(quantities(1.0e-6, 1.0)),
                new SimulationClock(1_000_000.0, 1.0e-9));

        assertTrue(output.time().size() <= MAX_TIMELINE_SAMPLES);
        assertFalse(output.time().isEmpty());
        assertEquals(2, output.scalarOutputs().size());
        assertTrue(output.values().isEmpty());
    }

    private static CanonicalQuantityBag quantities(double charge, double distance) {
        return new CanonicalQuantityBag(
                Map.of("charge", BigDecimal.valueOf(charge), "distance", BigDecimal.valueOf(distance)),
                Map.of("charge", "C", "distance", "m"));
    }
}
