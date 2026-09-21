package com.example.backend.physics.compatibility.legacy.reference.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.circuits.DiodeParameters;
import com.example.backend.physics.compatibility.legacy.model.circuits.SensorOpAmpParameters;
import com.example.backend.physics.compatibility.legacy.model.circuits.ThermistorParameters;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.example.backend.physics.compatibility.legacy.reference.TopicReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Circuit-topic application reference oracle. */
@Component
public class CircuitApplicationsReferenceSolver implements TopicReferenceSolver {
    @Override public String solverId() { return "circuit_applications_reference"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("diode_characteristic", "sensor_op_amp", "thermistor_response"); }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double time) {
        String model = PhysicsValues.model(specification); Map<String, Double> values = new LinkedHashMap<>();
        switch (model) {
            case "diode_characteristic" -> { DiodeParameters p = DiodeParameters.from(specification, overrides); values.put("thermalVoltage", p.thermalVoltage()); values.put("current", p.current()); values.put("power", p.power()); }
            case "sensor_op_amp" -> { SensorOpAmpParameters p = SensorOpAmpParameters.from(specification, overrides); values.put("sensorVoltage", p.sensorVoltage()); values.put("referenceVoltage", p.referenceVoltage()); values.put("amplifiedOutput", p.amplifiedOutput()); values.put("ledState", p.ledState()); values.put("sensorPower", p.sensorPower()); }
            case "thermistor_response" -> { ThermistorParameters p = ThermistorParameters.from(specification, overrides); values.put("resistance", p.resistance()); values.put("dividerVoltage", p.dividerVoltage()); values.put("sensorPower", p.sensorPower()); }
            default -> throw new IllegalArgumentException("Unsupported circuit application model: " + model);
        }
        return new AnalyticalPoint(values);
    }
}
