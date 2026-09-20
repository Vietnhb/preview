package com.example.backend.physics.module.optics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnellRefractionModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final SnellRefractionModule module = new SnellRefractionModule();

    @Test
    void registeredBindingMatchesSnellGoldenCaseAtThirtyDegrees() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                "refraction_solver", "refraction_reference", quantities(1.0, 1.5, Math.PI / 6.0));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(List.of(Math.asin(1.0 / 3.0), Math.asin(1.0 / 3.0), Math.asin(1.0 / 3.0)),
                output.values().get("refractedAngle"));
        assertEquals(List.of(0.0, 0.0, 0.0), output.values().get("totalInternalReflection"));
        assertEquals(List.of(Math.PI / 6.0, Math.PI / 6.0, Math.PI / 6.0), output.values().get("reflectedAngle"));

        var oracle = bound.reference(0.5).values();
        assertEquals(Math.asin(1.0 / 3.0), oracle.get("refractedAngle"), TOLERANCE);
        assertEquals(0.0, oracle.get("totalInternalReflection"), TOLERANCE);
        assertEquals(Math.PI / 6.0, oracle.get("reflectedAngle"), TOLERANCE);
    }

    @Test
    void normalIncidenceAndCriticalAngleArePhysicalBoundaryCases() {
        var normal = module.bind(quantities(1.0, 1.5, 0.0));
        assertEquals(0.0, module.solve(normal, new SimulationClock(0.1, 0.1))
                .values().get("refractedAngle").get(0), TOLERANCE);

        double criticalAngle = Math.asin(1.0 / 1.5);
        var critical = module.bind(quantities(1.5, 1.0, criticalAngle));
        var criticalOutput = module.solve(critical, new SimulationClock(0.1, 0.1));
        assertEquals(Math.PI / 2.0, criticalOutput.values().get("refractedAngle").get(0), TOLERANCE);
        assertEquals(0.0, criticalOutput.values().get("totalInternalReflection").get(0), TOLERANCE);
    }

    @Test
    void totalInternalReflectionHasNoTransmittedRayAndKeepsSpecularReflectionAngle() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                "refraction_solver", "refraction_reference", quantities(1.5, 1.0, Math.PI / 3.0));
        SolverOutput output = bound.solve(new SimulationClock(0.1, 0.1));

        assertEquals(0.0, output.values().get("refractedAngle").get(0), TOLERANCE);
        assertEquals(1.0, output.values().get("totalInternalReflection").get(0), TOLERANCE);
        assertEquals(Math.PI / 3.0, output.values().get("reflectedAngle").get(0), TOLERANCE);
        assertEquals(1.0, bound.reference(0.0).values().get("totalInternalReflection"), TOLERANCE);
        assertEquals(0.0, bound.reference(0.0).values().get("refractedAngle"), TOLERANCE);
    }

    @Test
    void rejectsNonpositiveIndexOutOfDomainAngleAndNoncanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 0.0, 0.2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 1.5, Math.PI / 2.0 + 1.0e-6)));
        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("refractive_index_1", bd(1.0), "refractive_index_2", bd(1.5), "incident_angle", bd(0.2)),
                Map.of("refractive_index_1", "1", "refractive_index_2", "1", "incident_angle", "deg"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    private static CanonicalQuantityBag quantities(double n1, double n2, double incidentAngle) {
        return new CanonicalQuantityBag(
                Map.of("refractive_index_1", bd(n1), "refractive_index_2", bd(n2),
                        "incident_angle", bd(incidentAngle)),
                Map.of("refractive_index_1", "1", "refractive_index_2", "1", "incident_angle", "rad"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
