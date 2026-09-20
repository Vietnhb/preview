package com.example.backend.physics.module.electromagnetism;

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

class MagneticForceModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final MagneticForceModule module = new MagneticForceModule();

    @Test
    void typedBindingProducesHandCalculatedForceVectorAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                MagneticForceModule.NUMERICAL_SOLVER_ID,
                MagneticForceModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 3.0, 4.0, Math.PI / 2.0));
        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));

        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("magneticForce", "magneticForceX", "magneticForceY", "magneticForceZ"),
                output.scalarOutputs().keySet());
        assertEquals(Map.of("magneticForce", 24.0, "magneticForceX", 0.0,
                "magneticForceY", 0.0, "magneticForceZ", 24.0), output.scalarOutputs());
        assertEquals(Map.of(), output.values());

        Map<String, Double> reference = bound.reference(0.5).values();
        assertEquals(output.scalarOutputs().keySet(), reference.keySet());
        output.scalarOutputs().forEach((key, scalar) -> assertEquals(scalar, reference.get(key), TOLERANCE, key));
    }

    @Test
    void forceVectorHasMagnitudeAndIsOrthogonalToVelocityAndMagneticField() {
        SolverOutput output = module.solve(module.bind(quantities(-2.0, 3.0, 4.0, Math.PI / 3.0)),
                new SimulationClock(0.2, 0.1));
        double forceX = output.scalarOutputs().get("magneticForceX");
        double forceY = output.scalarOutputs().get("magneticForceY");
        double forceZ = output.scalarOutputs().get("magneticForceZ");
        double fieldX = 4.0 * Math.cos(Math.PI / 3.0);
        double fieldY = 4.0 * Math.sin(Math.PI / 3.0);

        assertEquals(24.0 * Math.sin(Math.PI / 3.0), output.scalarOutputs().get("magneticForce"), TOLERANCE);
        assertEquals(output.scalarOutputs().get("magneticForce"),
                Math.hypot(Math.hypot(forceX, forceY), forceZ), TOLERANCE);
        assertEquals(0.0, forceX * 3.0, TOLERANCE);
        assertEquals(0.0, forceX * fieldX + forceY * fieldY + forceZ * 0.0, TOLERANCE);
        assertEquals(-output.scalarOutputs().get("magneticForce"), forceZ, TOLERANCE);
    }

    @Test
    void parallelAndAntiparallelFieldsProduceZeroForce() {
        for (double angle : List.of(0.0, Math.PI)) {
            SolverOutput output = module.solve(module.bind(quantities(2.0, 3.0, 4.0, angle)),
                    new SimulationClock(0.1, 0.1));
            assertEquals(0.0, output.scalarOutputs().get("magneticForce"), TOLERANCE);
            assertEquals(0.0, output.scalarOutputs().get("magneticForceZ"), TOLERANCE);
        }
    }

    @Test
    void referenceRemainsStableForNearParallelAndNearAntiparallelFields() {
        for (double angle : List.of(1.0e-8, Math.PI - 1.0e-8)) {
            var parameters = module.bind(quantities(1.0, 100_000.0, 1.0, angle));
            SolverOutput output = module.solve(parameters, new SimulationClock(0.1, 0.1));
            double numerical = output.scalarOutputs().get("magneticForce");
            double reference = module.referenceAt(parameters, 0.0).values().get("magneticForce");
            assertEquals(0.001, numerical, 1.0e-10);
            assertEquals(numerical, reference, 1.0e-10);
        }
    }

    @Test
    void rejectsMissingOrWrongUnitsAndInvalidPhysicalDomains() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2.0, -3.0, 4.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2.0, 3.0, -4.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2.0, 3.0, 4.0, Math.PI + 0.01)));
        assertThrows(IllegalArgumentException.class,
                () -> new MagneticForceModule.Parameters(Double.NaN, 3.0, 4.0, 1.0));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("charge", bd(2.0), "speed", bd(3.0), "magnetic_field", bd(4.0),
                        "velocity_field_angle", bd(1.0)),
                Map.of("charge", "C", "speed", "m/s", "magnetic_field", "G",
                        "velocity_field_angle", "rad"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsOverflowAndInvalidReferenceTime() {
        var extreme = module.bind(quantities(1.0e308, 1.0e308, 1.0, Math.PI / 2.0));
        assertThrows(ArithmeticException.class, () -> module.solve(extreme, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 0.0));

        var valid = module.bind(quantities(2.0, 3.0, 4.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    private static CanonicalQuantityBag quantities(double charge, double speed, double field, double angle) {
        return new CanonicalQuantityBag(
                Map.of("charge", bd(charge), "speed", bd(speed), "magnetic_field", bd(field),
                        "velocity_field_angle", bd(angle)),
                Map.of("charge", "C", "speed", "m/s", "magnetic_field", "T",
                        "velocity_field_angle", "rad"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
