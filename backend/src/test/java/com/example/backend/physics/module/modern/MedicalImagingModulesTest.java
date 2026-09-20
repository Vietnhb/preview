package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MedicalImagingModulesTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void xrayBeerLambertOutputsMatchHandValuesAndIndependentOracle() {
        XrayImagingModule module = new XrayImagingModule();
        var parameters = module.bind(quantities(
                Map.of("incident_intensity", 8.0, "attenuation_coefficient", 0.5,
                        "material_thickness", 2.0, "exposure_time", 3.0),
                Map.of("incident_intensity", "1", "attenuation_coefficient", "1/m",
                        "material_thickness", "m", "exposure_time", "s")));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.2, 0.1));

        assertEquals(3, output.time().size());
        assertEquals(8.0 * Math.exp(-1.0), output.values().get("transmittedIntensity").get(0), TOLERANCE);
        assertEquals(1.0 - Math.exp(-1.0), output.values().get("absorbedFraction").get(0), TOLERANCE);
        assertEquals(24.0 * Math.exp(-1.0), output.values().get("detectorDoseProxy").get(0), TOLERANCE);
        assertEquals(2.0 * Math.log(2.0), output.values().get("halfValueLayer").get(0), TOLERANCE);
        var oracle = module.referenceAt(parameters, 0.1).values();
        output.values().forEach((key, series) -> assertEquals(series.get(0), oracle.get(key), TOLERANCE, key));
    }

    @Test
    void xrayZeroThicknessIsBoundaryAndInvalidPhysicalDomainsAreRejected() {
        XrayImagingModule module = new XrayImagingModule();
        var noMaterial = module.bind(quantities(
                Map.of("incident_intensity", 5.0, "attenuation_coefficient", 2.0,
                        "material_thickness", 0.0, "exposure_time", 4.0),
                Map.of("incident_intensity", "1", "attenuation_coefficient", "1/m",
                        "material_thickness", "m", "exposure_time", "s")));
        var boundary = module.solve(noMaterial, new SimulationClock(0.1, 0.1));
        assertEquals(5.0, boundary.values().get("transmittedIntensity").get(0), 0.0);
        assertEquals(0.0, boundary.values().get("absorbedFraction").get(0), 0.0);
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                Map.of("incident_intensity", 5.0, "attenuation_coefficient", 0.0,
                        "material_thickness", 1.0, "exposure_time", 1.0),
                Map.of("incident_intensity", "1", "attenuation_coefficient", "1/m",
                        "material_thickness", "m", "exposure_time", "s"))));
        assertThrows(IllegalArgumentException.class, () -> new XrayImagingModule.Parameters(1.0, 1.0, -1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new XrayImagingModule.Parameters(1.0, 1.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(noMaterial, Double.NaN));
    }

    @Test
    void ctProjectionAndReconstructionMatchLogAndAngularGoldenValues() {
        CtReconstructionModule module = new CtReconstructionModule();
        var parameters = module.bind(quantities(
                Map.of("incident_intensity", 10.0, "attenuation_coefficient", 0.5,
                        "path_length", 4.0, "projection_count", 8.0),
                Map.of("incident_intensity", "1", "attenuation_coefficient", "1/m",
                        "path_length", "m", "projection_count", "1")));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.1, 0.1));

        assertEquals(10.0 * Math.exp(-2.0), output.values().get("transmittedIntensity").get(0), TOLERANCE);
        assertEquals(2.0, output.values().get("lineIntegral").get(0), TOLERANCE);
        assertEquals(Math.PI / 4.0, output.values().get("angularStep").get(0), TOLERANCE);
        assertEquals(0.5, output.values().get("reconstructedAttenuation").get(0), 0.0);
        var oracle = module.referenceAt(parameters, 0.0).values();
        output.values().forEach((key, series) -> assertEquals(series.get(0), oracle.get(key), TOLERANCE, key));
    }

    @Test
    void ctZeroAttenuationIsBoundaryAndProjectionCountMustBePositiveInteger() {
        CtReconstructionModule module = new CtReconstructionModule();
        var zeroAttenuation = module.bind(quantities(
                Map.of("incident_intensity", 7.0, "attenuation_coefficient", 0.0,
                        "path_length", 3.0, "projection_count", 1.0),
                Map.of("incident_intensity", "1", "attenuation_coefficient", "1/m",
                        "path_length", "m", "projection_count", "1")));
        var boundary = module.solve(zeroAttenuation, new SimulationClock(0.1, 0.1));
        assertEquals(7.0, boundary.values().get("transmittedIntensity").get(0), 0.0);
        assertEquals(0.0, boundary.values().get("lineIntegral").get(0), 0.0);
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                Map.of("incident_intensity", 1.0, "attenuation_coefficient", 1.0,
                        "path_length", 1.0, "projection_count", 2.5),
                Map.of("incident_intensity", "1", "attenuation_coefficient", "1/m",
                        "path_length", "m", "projection_count", "1"))));
        assertThrows(IllegalArgumentException.class,
                () -> new CtReconstructionModule.Parameters(1.0, 0.0, 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(zeroAttenuation, -1.0));
    }

    @Test
    void mriRelaxationMatchesHandCalculatedLongitudinalAndTransverseCurves() {
        MriRelaxationModule module = new MriRelaxationModule();
        var parameters = module.bind(quantities(
                Map.of("equilibrium_magnetization", 1.0, "longitudinal_relaxation_time", 2.0,
                        "transverse_relaxation_time", 4.0, "echo_time", 4.0),
                Map.of("equilibrium_magnetization", "1", "longitudinal_relaxation_time", "s",
                        "transverse_relaxation_time", "s", "echo_time", "s")));
        SolverOutput output = module.solve(parameters, new SimulationClock(1.0, 0.5));

        assertEquals(0.0, output.values().get("longitudinalMagnetization").get(0), 0.0);
        assertEquals(1.0, output.values().get("transverseMagnetization").get(0), 0.0);
        assertEquals(1.0 - Math.exp(-0.25), output.values().get("longitudinalMagnetization").get(1), TOLERANCE);
        assertEquals(Math.exp(-0.125), output.values().get("transverseMagnetization").get(1), TOLERANCE);
        assertEquals(Math.exp(-1.0), output.values().get("echoSignal").get(0), TOLERANCE);
        var oracle = module.referenceAt(parameters, 0.5).values();
        assertEquals(output.values().get("longitudinalMagnetization").get(1),
                oracle.get("longitudinalMagnetization"), TOLERANCE);
        assertEquals(output.values().get("transverseMagnetization").get(1),
                oracle.get("transverseMagnetization"), TOLERANCE);
    }

    @Test
    void mriZeroMagnetizationIsBoundaryAndInvalidRelaxationTimesAreRejected() {
        MriRelaxationModule module = new MriRelaxationModule();
        var zeroSignal = module.bind(quantities(
                Map.of("equilibrium_magnetization", 0.0, "longitudinal_relaxation_time", 2.0,
                        "transverse_relaxation_time", 3.0, "echo_time", 0.0),
                Map.of("equilibrium_magnetization", "1", "longitudinal_relaxation_time", "s",
                        "transverse_relaxation_time", "s", "echo_time", "s")));
        var boundary = module.solve(zeroSignal, new SimulationClock(0.1, 0.1));
        assertEquals(java.util.List.of(0.0, 0.0), boundary.values().get("longitudinalMagnetization"));
        assertEquals(java.util.List.of(0.0, 0.0), boundary.values().get("transverseMagnetization"));
        assertThrows(IllegalArgumentException.class,
                () -> new MriRelaxationModule.Parameters(1.0, 0.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new MriRelaxationModule.Parameters(1.0, 1.0, 1.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(zeroSignal, Double.POSITIVE_INFINITY));
    }

    private static CanonicalQuantityBag quantities(Map<String, Double> values, Map<String, String> units) {
        Map<String, BigDecimal> decimals = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> decimals.put(key, BigDecimal.valueOf(value)));
        return new CanonicalQuantityBag(decimals, units);
    }
}
