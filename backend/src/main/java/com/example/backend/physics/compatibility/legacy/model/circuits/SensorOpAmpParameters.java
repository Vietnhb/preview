package com.example.backend.physics.compatibility.legacy.model.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Voltage-divider sensor followed by an ideal saturated op-amp comparator. */
public record SensorOpAmpParameters(double supplyVoltage, double sensorResistance,
                                    double referenceResistance, double opAmpGain,
                                    double thresholdVoltage) {
    public static SensorOpAmpParameters from(JsonNode specification, Map<String, Double> overrides) {
        double supply = PhysicsValues.require(specification, overrides, "supply_voltage");
        double sensor = PhysicsValues.require(specification, overrides, "sensor_resistance");
        double reference = PhysicsValues.require(specification, overrides, "reference_resistance");
        double gain = PhysicsValues.require(specification, overrides, "op_amp_gain");
        double threshold = PhysicsValues.require(specification, overrides, "threshold_voltage");
        if (supply <= 0 || sensor <= 0 || reference <= 0 || gain <= 0
                || !Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Sensor/op-amp parameters are invalid");
        }
        return new SensorOpAmpParameters(supply, sensor, reference, gain, threshold);
    }
    public double sensorVoltage() { return supplyVoltage * sensorResistance / (sensorResistance + referenceResistance); }
    public double referenceVoltage() { return supplyVoltage * referenceResistance / (sensorResistance + referenceResistance); }
    public double amplifiedOutput() {
        return Math.clamp(opAmpGain * (sensorVoltage() - referenceVoltage()), 0, supplyVoltage);
    }
    public double ledState() { return amplifiedOutput() >= thresholdVoltage ? 1 : 0; }
    public double sensorPower() { return supplyVoltage * supplyVoltage / (sensorResistance + referenceResistance); }
}
