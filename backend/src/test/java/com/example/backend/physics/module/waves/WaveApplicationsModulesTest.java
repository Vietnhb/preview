package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveApplicationsModulesTest {

    @Test
    void radioCommunicationMatchesCarrierAndSidebandGoldenAndReference() {
        RadioCommunicationModule module = new RadioCommunicationModule();
        var parameters = module.bind(values(
                Map.of("carrier_frequency", 100.0e6, "modulation_frequency", 10.0e3,
                        "modulation_index", 0.5),
                Map.of("carrier_frequency", "Hz", "modulation_frequency", "Hz", "modulation_index", "1")));
        var output = module.solve(parameters, new SimulationClock(0.2, 0.1));
        assertEquals(3, output.time().size());
        assertEquals(2.99792458, output.values().get("wavelength").get(0), 1.0e-12);
        assertEquals(1.0e-8, output.values().get("period").get(0), 1.0e-23);
        assertEquals(2.0 * Math.PI * 100.0e6, output.values().get("angularFrequency").get(0), 1.0e-7);
        assertEquals(99_990_000.0, output.values().get("lowerSideband").get(0), 0.0);
        assertEquals(100_010_000.0, output.values().get("upperSideband").get(0), 0.0);
        var oracle = module.referenceAt(parameters, 0.1).values();
        output.values().forEach((key, series) -> assertRelativeEquals(series.get(0), oracle.get(key), 2.0e-15, key));
    }

    @Test
    void radioCommunicationAllowsZeroDepthButRejectsInvalidModulation() {
        RadioCommunicationModule module = new RadioCommunicationModule();
        var unmodulated = module.bind(values(
                Map.of("carrier_frequency", 1.0e6, "modulation_frequency", 1.0e3,
                        "modulation_index", 0.0),
                Map.of("carrier_frequency", "Hz", "modulation_frequency", "Hz", "modulation_index", "1")));
        assertTrue(module.solve(unmodulated, new SimulationClock(0.1, 0.1))
                .values().containsKey("upperSideband"));
        assertThrows(IllegalArgumentException.class,
                () -> new RadioCommunicationModule.Parameters(100.0, 100.0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new RadioCommunicationModule.Parameters(100.0, 1.0, 1.01));
        assertThrows(IllegalArgumentException.class,
                () -> new RadioCommunicationModule.Parameters(100.0, 0.0, 0.5));
    }

    @Test
    void radioSignalChainMatchesCarsonAndDecibelAttenuationGolden() {
        RadioSignalChainModule module = new RadioSignalChainModule();
        var parameters = module.bind(values(
                Map.of("carrier_frequency", 100.0e6, "modulation_frequency", 10.0e3,
                        "frequency_deviation", 5.0e3, "modulation_index", 0.5,
                        "signal_amplitude", 2.0, "path_length", 1000.0,
                        "attenuation_db_per_meter", 0.001),
                Map.of("carrier_frequency", "Hz", "modulation_frequency", "Hz",
                        "frequency_deviation", "Hz", "modulation_index", "1",
                        "signal_amplitude", "V", "path_length", "m",
                        "attenuation_db_per_meter", "dB/m")));
        var output = module.solve(parameters, new SimulationClock(0.1, 0.1));
        assertEquals(2.99792458, output.values().get("wavelength").get(0), 1.0e-12);
        assertEquals(99_990_000.0, output.values().get("lowerSideband").get(0), 0.0);
        assertEquals(100_010_000.0, output.values().get("upperSideband").get(0), 0.0);
        assertEquals(0.5, output.values().get("fmModulationIndex").get(0), 0.0);
        assertEquals(30_000.0, output.values().get("carsonBandwidth").get(0), 0.0);
        assertEquals(Math.pow(10.0, -0.05), output.values().get("attenuationFactor").get(0), 1.0e-15);
        assertEquals(2.0 * Math.pow(10.0, -0.05), output.values().get("receivedAmplitude").get(0), 1.0e-15);
        var oracle = module.referenceAt(parameters, 0.0).values();
        output.values().forEach((key, series) -> assertRelativeEquals(series.get(0), oracle.get(key), 2.0e-15, key));
    }

    @Test
    void radioSignalChainZeroPathIsLosslessAndDomainsAreChecked() {
        RadioSignalChainModule module = new RadioSignalChainModule();
        var lossless = module.bind(values(
                Map.of("carrier_frequency", 1000.0, "modulation_frequency", 100.0,
                        "frequency_deviation", 0.0, "modulation_index", 0.0,
                        "signal_amplitude", 3.0, "path_length", 0.0,
                        "attenuation_db_per_meter", 4.0),
                Map.of("carrier_frequency", "Hz", "modulation_frequency", "Hz",
                        "frequency_deviation", "Hz", "modulation_index", "1",
                        "signal_amplitude", "V", "path_length", "m",
                        "attenuation_db_per_meter", "dB/m")));
        var output = module.solve(lossless, new SimulationClock(0.1, 0.1));
        assertEquals(1.0, output.values().get("attenuationFactor").get(0), 0.0);
        assertEquals(3.0, output.values().get("receivedAmplitude").get(0), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> new RadioSignalChainModule.Parameters(100.0, 100.0, 1.0, 0.0, 1.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RadioSignalChainModule.Parameters(100.0, 1.0, -1.0, 0.0, 1.0, 0.0, 0.0));
    }

    @Test
    void ultrasoundPulseEchoMatchesHandDepthWavelengthAndPeriod() {
        UltrasoundImagingModule module = new UltrasoundImagingModule();
        var parameters = module.bind(values(
                Map.of("sound_speed", 1500.0, "frequency", 3.0e6, "echo_time", 2.0e-4),
                Map.of("sound_speed", "m/s", "frequency", "Hz", "echo_time", "s")));
        var output = module.solve(parameters, new SimulationClock(0.2, 0.1));
        assertEquals(5.0e-4, output.values().get("wavelength").get(0), 1.0e-18);
        assertEquals(0.15, output.values().get("depth").get(0), 1.0e-15);
        assertEquals(1.0 / 3.0e6, output.values().get("period").get(0), 1.0e-22);
        var oracle = module.referenceAt(parameters, 0.1).values();
        output.values().forEach((key, series) -> assertRelativeEquals(series.get(0), oracle.get(key), 2.0e-15, key));
    }

    @Test
    void ultrasoundZeroEchoIsSurfaceBoundaryAndInvalidInputsAreRejected() {
        UltrasoundImagingModule module = new UltrasoundImagingModule();
        var surface = module.bind(values(
                Map.of("sound_speed", 1500.0, "frequency", 3.0e6, "echo_time", 0.0),
                Map.of("sound_speed", "m/s", "frequency", "Hz", "echo_time", "s")));
        assertEquals(0.0, module.solve(surface, new SimulationClock(0.1, 0.1))
                .values().get("depth").get(0), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> new UltrasoundImagingModule.Parameters(0.0, 3.0e6, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new UltrasoundImagingModule.Parameters(1500.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new UltrasoundImagingModule.Parameters(1500.0, 3.0e6, -1.0));
    }

    private static void assertRelativeEquals(double expected, double actual, double relativeTolerance, String key) {
        double scale = Math.max(Math.abs(expected), Math.abs(actual));
        assertEquals(expected, actual, scale * relativeTolerance + 1.0e-300, key);
    }

    private static CanonicalQuantityBag values(Map<String, Double> raw, Map<String, String> units) {
        Map<String, BigDecimal> decimals = new java.util.LinkedHashMap<>();
        raw.forEach((key, value) -> decimals.put(key, BigDecimal.valueOf(value)));
        return new CanonicalQuantityBag(decimals, units);
    }
}
