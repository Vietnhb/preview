package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitInstrumentModulesTest {
    private static final double TOLERANCE = 1.0e-10;

    @Test
    void thermistorGoldenAtReferenceTemperatureHasEqualDividerAndIndependentPowerOracle() {
        var module = new ThermistorResponseModule();
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                ThermistorResponseModule.NUMERICAL_SOLVER_ID,
                ThermistorResponseModule.REFERENCE_SOLVER_ID,
                quantities(Map.of("reference_resistance", 10_000.0, "reference_temperature", 298.15,
                        "beta_constant", 3950.0, "temperature", 25.0, "supply_voltage", 5.0,
                        "divider_resistance", 10_000.0), Map.of("reference_resistance", "ohm",
                        "reference_temperature", "K", "beta_constant", "K", "temperature", "degC",
                        "supply_voltage", "V", "divider_resistance", "ohm")));
        var output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(10_000.0, output.scalarOutputs().get("resistance"), TOLERANCE);
        assertEquals(2.5, output.scalarOutputs().get("dividerVoltage"), TOLERANCE);
        assertEquals(0.000625, output.scalarOutputs().get("sensorPower"), TOLERANCE);
        assertTrue(output.values().isEmpty(), "steady outputs must remain scalar values");
        var oracle = bound.reference(0.1).values();
        output.scalarOutputs().forEach((key, value) -> assertEquals(value, oracle.get(key), TOLERANCE, key));
    }

    @Test
    void thermistorRejectsAbsoluteZeroAndNoncanonicalUnits() {
        var module = new ThermistorResponseModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                Map.of("reference_resistance", 10_000.0, "reference_temperature", 298.15,
                        "beta_constant", 3950.0, "temperature", -273.15, "supply_voltage", 5.0,
                        "divider_resistance", 10_000.0), Map.of("reference_resistance", "ohm",
                        "reference_temperature", "K", "beta_constant", "K", "temperature", "degC",
                        "supply_voltage", "V", "divider_resistance", "ohm"))));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                Map.of("reference_resistance", 10_000.0, "reference_temperature", 298.15,
                        "beta_constant", 3950.0, "temperature", 25.0, "supply_voltage", 5.0,
                        "divider_resistance", 10_000.0), Map.of("reference_resistance", "kohm",
                        "reference_temperature", "K", "beta_constant", "K", "temperature", "degC",
                        "supply_voltage", "V", "divider_resistance", "ohm"))));
    }

    @Test
    void sensorDividerAndOpAmpSaturateAtSupplyRailAndMatchIndependentMidpointOracle() {
        var module = new SensorOpAmpModule();
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                SensorOpAmpModule.NUMERICAL_SOLVER_ID, SensorOpAmpModule.REFERENCE_SOLVER_ID,
                quantities(Map.of("supply_voltage", 5.0, "sensor_resistance", 3000.0,
                        "reference_resistance", 1000.0, "op_amp_gain", 4.0, "threshold_voltage", 3.0),
                        Map.of("supply_voltage", "V", "sensor_resistance", "ohm",
                                "reference_resistance", "ohm", "op_amp_gain", "1", "threshold_voltage", "V")));
        var output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(3.75, output.scalarOutputs().get("sensorVoltage"), TOLERANCE);
        assertEquals(1.25, output.scalarOutputs().get("referenceVoltage"), TOLERANCE);
        assertEquals(5.0, output.scalarOutputs().get("amplifiedOutput"), TOLERANCE);
        assertEquals(1.0, output.scalarOutputs().get("ledState"), TOLERANCE);
        assertEquals(0.00625, output.scalarOutputs().get("sensorPower"), TOLERANCE);
        var oracle = bound.reference(0.5).values();
        output.scalarOutputs().forEach((key, value) -> assertEquals(value, oracle.get(key), TOLERANCE, key));
    }

    @Test
    void sensorOpAmpZeroDifferentialBoundaryAndInvalidInputsAreHandled() {
        var module = new SensorOpAmpModule();
        var equalDivider = module.bind(quantities(Map.of("supply_voltage", 12.0, "sensor_resistance", 1000.0,
                "reference_resistance", 1000.0, "op_amp_gain", 1000.0, "threshold_voltage", 0.0),
                Map.of("supply_voltage", "V", "sensor_resistance", "ohm", "reference_resistance", "ohm",
                        "op_amp_gain", "1", "threshold_voltage", "V")));
        var output = module.solve(equalDivider, new SimulationClock(0.1, 0.1));
        assertEquals(6.0, output.scalarOutputs().get("sensorVoltage"), TOLERANCE);
        assertEquals(0.0, output.scalarOutputs().get("amplifiedOutput"), TOLERANCE);
        assertEquals(1.0, output.scalarOutputs().get("ledState"), TOLERANCE);
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                Map.of("supply_voltage", 12.0, "sensor_resistance", 0.0, "reference_resistance", 1000.0,
                        "op_amp_gain", 1.0, "threshold_voltage", 1.0), Map.of("supply_voltage", "V",
                        "sensor_resistance", "ohm", "reference_resistance", "ohm", "op_amp_gain", "1",
                        "threshold_voltage", "V"))));
    }

    @Test
    void acRlcAtResonanceHasUnityPowerFactorAndTheExpectedRealPower() {
        var module = new AcRlcCircuitModule();
        var bag = quantities(Map.of("resistance", 4.0, "inductance", 1.0 / (2.0 * Math.PI),
                "capacitance", 1.0 / (2.0 * Math.PI), "frequency", 1.0, "rms_voltage", 12.0),
                Map.of("resistance", "ohm", "inductance", "H", "capacitance", "F",
                        "frequency", "Hz", "rms_voltage", "V"));
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                AcRlcCircuitModule.NUMERICAL_SOLVER_ID, AcRlcCircuitModule.REFERENCE_SOLVER_ID, bag);
        var output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(4.0, output.scalarOutputs().get("impedance"), TOLERANCE);
        assertEquals(3.0, output.scalarOutputs().get("rmsCurrent"), TOLERANCE);
        assertEquals(1.0, output.scalarOutputs().get("powerFactor"), TOLERANCE);
        assertEquals(36.0, output.scalarOutputs().get("realPower"), TOLERANCE);
        assertEquals(0.0, output.scalarOutputs().get("phase"), TOLERANCE);
        var oracle = bound.reference(0.1).values();
        output.scalarOutputs().forEach((key, value) -> assertEquals(value, oracle.get(key), TOLERANCE, key));
    }

    @Test
    void acRlcRejectsZeroReactiveComponentAndAllowsZeroSourceVoltageBoundary() {
        var module = new AcRlcCircuitModule();
        var parameters = module.bind(quantities(Map.of("resistance", 4.0, "inductance", 1.0,
                "capacitance", 1.0, "frequency", 1.0, "rms_voltage", 0.0),
                Map.of("resistance", "ohm", "inductance", "H", "capacitance", "F",
                        "frequency", "Hz", "rms_voltage", "V")));
        var zeroSource = module.solve(parameters, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, zeroSource.scalarOutputs().get("rmsCurrent"), 0.0);
        assertEquals(0.0, zeroSource.scalarOutputs().get("realPower"), 0.0);
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(Map.of("resistance", 0.0,
                "inductance", 1.0, "capacitance", 1.0, "frequency", 1.0, "rms_voltage", 1.0),
                Map.of("resistance", "ohm", "inductance", "H", "capacitance", "F",
                        "frequency", "Hz", "rms_voltage", "V"))));
    }

    private static CanonicalQuantityBag quantities(Map<String, Double> values, Map<String, String> units) {
        var decimals = new java.util.LinkedHashMap<String, BigDecimal>();
        values.forEach((key, value) -> decimals.put(key, BigDecimal.valueOf(value)));
        return new CanonicalQuantityBag(decimals, units);
    }
}
