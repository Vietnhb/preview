package com.example.backend.physics.module.modern;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.service.problem.SchemaCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeBroglieDiffractionModuleTest {
    private final DeBroglieDiffractionModule module = new DeBroglieDiffractionModule();

    @Test
    void matchesHandCalculatedElectronMatterWaveAndIndependentEnergyOracle() {
        var parameters = module.bind(quantities(9.1093837e-31, 1.0e6, 2.0e-9, 1.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(1.0, 0.1));

        assertEquals(java.util.List.of(0.0), output.time());
        assertEquals(9.1093837e-25, output.values().get("momentum").get(0), 1.0e-38);
        assertEquals(7.273895104451467e-10, output.values().get("wavelength").get(0), 1.0e-22);
        assertEquals(4.55469185e-19, output.values().get("kineticEnergy").get(0), 1.0e-32);
        assertEquals(0.3722312175158396, output.values().get("diffractionAngle").get(0), 1.0e-14);
        assertEquals(1.0, output.values().get("diffractionAllowed").get(0), 0.0);

        Map<String, Double> reference = module.referenceAt(parameters, 0.0).values();
        assertRelativeEquals(output.values().get("momentum").get(0), reference.get("momentum"), 2.0e-15);
        assertRelativeEquals(output.values().get("wavelength").get(0), reference.get("wavelength"), 2.0e-15);
        assertRelativeEquals(output.values().get("kineticEnergy").get(0), reference.get("kineticEnergy"), 2.0e-15);
        assertEquals(output.values().get("diffractionAllowed").get(0), reference.get("diffractionAllowed"), 0.0);
        assertEquals(output.values().get("diffractionAngle").get(0), reference.get("diffractionAngle"), 1.0e-14);
    }

    @Test
    void criticalBraggAngleIsAllowedAndHigherOrderWithoutSolutionIsFinite() {
        var exactlyCritical = module.bind(quantities(1.0, 1.0, 6.62607015e-34, 1.0));
        var criticalOutput = module.solve(exactlyCritical, new SimulationClock(0.1, 0.1));
        assertEquals(1.0, criticalOutput.values().get("diffractionAllowed").get(0), 0.0);
        assertEquals(Math.PI / 2.0, criticalOutput.values().get("diffractionAngle").get(0), 1.0e-15);
        assertEquals(1.0, module.referenceAt(exactlyCritical, 0.0).values().get("diffractionAllowed"), 0.0);

        var forbidden = module.bind(quantities(1.0, 1.0, 3.313035075e-34, 1.0));
        var forbiddenOutput = module.solve(forbidden, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, forbiddenOutput.values().get("diffractionAllowed").get(0), 0.0);
        assertEquals(0.0, forbiddenOutput.values().get("diffractionAngle").get(0), 0.0);
        assertEquals(0.0, module.referenceAt(forbidden, 0.0).values().get("diffractionAllowed"), 0.0);
    }

    @Test
    void rejectsNoncanonicalUnitsFractionalOrderAndRelativisticDomain() {
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                9.1093837e-31, 1.0e6, 2.0e-9, 1.5)));
        assertThrows(IllegalArgumentException.class, () -> new DeBroglieDiffractionModule.Parameters(
                1.0, 299_792_458.0, 1.0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DeBroglieDiffractionModule.Parameters(1.0, Double.NaN, 1.0, 1));
        var parameters = module.bind(quantities(1.0, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.01));
    }

    @Test
    void schemaCompilesMomentumOutputUnitAndCatalogAlias() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UnitNormalizer units = new UnitNormalizer(mapper);
        assertTrue(units.isKnownUnit("kg*m/s"));
        var alias = units.normalize(BigDecimal.ONE, "N*s");
        assertTrue(alias.knownUnit());
        assertEquals("kg*m/s", alias.normalizedUnit());

        try (InputStream input = getClass().getResourceAsStream(
                "/schemas/source/MODERN_PHYSICS/115__de_broglie_diffraction__1.2.json")) {
            assertFalse(input == null, "Versioned source schema must be present");
            var source = mapper.readTree(input);
            ((com.fasterxml.jackson.databind.node.ObjectNode) source.path("definition"))
                    .put("model", source.path("model").asText());
            CompiledSchema compiled = new SchemaCompiler(mapper, units).compile(
                    source.path("definition"), source.path("schemaId").asText(),
                    source.path("version").asText(), source.path("topic").asText());
            assertEquals("kg*m/s", compiled.outputDefinitions().get("momentum").unit());
            assertEquals(5, compiled.outputDefinitions().size());
        }
    }

    private static CanonicalQuantityBag quantities(double mass, double speed, double spacing, double order) {
        return new CanonicalQuantityBag(
                Map.of("particle_mass", BigDecimal.valueOf(mass),
                        "particle_speed", BigDecimal.valueOf(speed),
                        "lattice_spacing", BigDecimal.valueOf(spacing),
                        "diffraction_order", BigDecimal.valueOf(order)),
                Map.of("particle_mass", "kg", "particle_speed", "m/s",
                        "lattice_spacing", "m", "diffraction_order", "1"));
    }

    private static void assertRelativeEquals(double expected, double actual, double relativeTolerance) {
        double scale = Math.max(Math.abs(expected), Math.abs(actual));
        assertTrue(Math.abs(expected - actual) <= scale * relativeTolerance,
                "expected " + expected + " but got " + actual);
    }
}
