package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.ScalarField;
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

class WaterSurfaceInterferenceModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final WaterSurfaceInterferenceModule module = new WaterSurfaceInterferenceModule();

    @Test
    void matchesHandCalculatedCenterAndIndependentMidpointOracle() {
        assertEquals("water_surface_interference", module.moduleId());
        assertEquals("water_surface_interference_solver_v2", module.numericalSolverId());
        assertEquals("water_surface_interference_reference_v2", module.referenceSolverId());

        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                WaterSurfaceInterferenceModule.NUMERICAL_SOLVER_ID,
                WaterSurfaceInterferenceModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 1.0, 0.5, 0.2, 2.0, 9));
        SolverOutput output = bound.solve(new SimulationClock(0.5, 0.25));

        assertEquals(List.of(0.0, 0.25, 0.5), output.time());
        assertEquals(List.of("centerHeight"), output.values().keySet().stream().toList());
        assertEquals(List.of("waterSurface"), output.scalarFields().keySet().stream().toList());
        ScalarField field = output.scalarFields().get("waterSurface");
        assertEquals(2, field.physicalDimension());
        assertEquals(List.of(3, 9, 9), field.shape());
        assertEquals(List.of("x", "y"), field.axes().stream().map(ScalarField.Axis::key).toList());
        assertEquals("m", field.valueUnit());
        assertEquals("s", field.timeUnit());

        double atZero = 0.4 * Math.cos(Math.PI / 4.0);
        double atQuarter = 0.4;
        assertEquals(atZero, output.values().get("centerHeight").getFirst(), TOLERANCE);
        assertEquals(atQuarter, output.values().get("centerHeight").get(1), TOLERANCE);
        assertEquals(atZero, field.values().getFirst().get(4 * 9 + 4), TOLERANCE);

        for (int index = 0; index < output.time().size(); index++) {
            double time = output.time().get(index);
            double expected = 0.4 * Math.cos(Math.PI / 4.0 - Math.PI * time);
            assertEquals(expected, bound.reference(time).values().get("centerHeight"), TOLERANCE);
            assertEquals(expected, output.values().get("centerHeight").get(index), TOLERANCE);
        }
    }

    @Test
    void rejectsInvalidCanonicalUnitsAndPhysicalOrGridDomains() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                2.0, 1.0, 0.5, 0.2, 2.0, 9, "cm", "m/s", "m", "m", "m", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 1.0, 0.5, 0.2, 2.0, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 0.0, 0.5, 0.2, 2.0, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 1.0, 0.0, 0.2, 2.0, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 1.0, 2.0, 0.2, 2.0, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 1.0, 0.5, -0.1, 2.0, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 1.0, 0.5, 0.2, 0.5, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 1.0, 0.5, 0.2, 2.0, 7)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 1.0, 0.5, 0.2, 2.0, 8.5)));

        var valid = module.bind(quantities(2.0, 1.0, 0.5, 0.2, 2.0, 9));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
    }

    @Test
    void acceptsSpatialSampleBoundaryAndRejectsExcessiveTwoDimensionalCellCount() {
        var maximumSpatial = module.bind(quantities(2.0, 1.0, 0.5, 0.2, 2.0, 128));
        SolverOutput boundaryOutput = module.solve(maximumSpatial, new SimulationClock(0.05, 0.05));
        assertEquals(List.of(2, 128, 128), boundaryOutput.scalarFields().get("waterSurface").shape());
        assertEquals(128,
                boundaryOutput.scalarFields().get("waterSurface").axes().getFirst().coordinates().size());

        var excessiveCells = module.bind(quantities(2.0, 1.0, 0.5, 0.2, 2.0, 128));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(excessiveCells, new SimulationClock(1.0, 0.01)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2.0, 1.0, 0.5, 0.2, 2.0, 129)));

        var overflow = module.bind(quantities(Double.MIN_VALUE, 1.0, 0.5, 0.2, 2.0, 8));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflow, new SimulationClock(0.1, 0.05)));
    }

    private static CanonicalQuantityBag quantities(double wavelength, double speed, double separation,
                                                    double amplitude, double domain, double samples) {
        return quantities(wavelength, speed, separation, amplitude, domain, samples,
                "m", "m/s", "m", "m", "m", "1");
    }

    private static CanonicalQuantityBag quantities(double wavelength, double speed, double separation,
                                                    double amplitude, double domain, double samples,
                                                    String wavelengthUnit, String speedUnit, String separationUnit,
                                                    String amplitudeUnit, String domainUnit, String samplesUnit) {
        Map<String, BigDecimal> values = Map.of(
                "wavelength", BigDecimal.valueOf(wavelength),
                "wave_speed", BigDecimal.valueOf(speed),
                "source_separation", BigDecimal.valueOf(separation),
                "amplitude", BigDecimal.valueOf(amplitude),
                "domain_size", BigDecimal.valueOf(domain),
                "spatial_samples", BigDecimal.valueOf(samples));
        Map<String, String> units = Map.of(
                "wavelength", wavelengthUnit,
                "wave_speed", speedUnit,
                "source_separation", separationUnit,
                "amplitude", amplitudeUnit,
                "domain_size", domainUnit,
                "spatial_samples", samplesUnit);
        return new CanonicalQuantityBag(values, units);
    }
}
