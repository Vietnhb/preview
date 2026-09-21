package com.example.backend.physics.compatibility.legacy.solver.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.circuits.DiodeParameters;
import com.example.backend.physics.compatibility.legacy.model.circuits.SensorOpAmpParameters;
import com.example.backend.physics.compatibility.legacy.model.circuits.ThermistorParameters;
import com.example.backend.physics.runtime.SimulationTimeline;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.example.backend.physics.compatibility.legacy.solver.TopicPhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Circuit-topic application family; no wave, modern, or practical dispatch. */
@Component
public class CircuitApplicationsSolver implements TopicPhysicsSolver {
    @Override public String solverId() { return "circuit_applications_solver"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("diode_characteristic", "sensor_op_amp", "thermistor_response"); }

    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
            double duration, double step) {
        String model = PhysicsValues.model(specification);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        switch (model) {
            case "diode_characteristic" -> {
                DiodeParameters p = DiodeParameters.from(specification, overrides);
                put(values, time, "thermalVoltage", p.thermalVoltage()); put(values, time, "current", p.current());
                put(values, time, "power", p.power());
            }
            case "sensor_op_amp" -> {
                SensorOpAmpParameters p = SensorOpAmpParameters.from(specification, overrides);
                put(values, time, "sensorVoltage", p.sensorVoltage()); put(values, time, "referenceVoltage", p.referenceVoltage());
                put(values, time, "amplifiedOutput", p.amplifiedOutput()); put(values, time, "ledState", p.ledState());
                put(values, time, "sensorPower", p.sensorPower());
            }
            case "thermistor_response" -> {
                ThermistorParameters p = ThermistorParameters.from(specification, overrides);
                put(values, time, "resistance", p.resistance()); put(values, time, "dividerVoltage", p.dividerVoltage());
                put(values, time, "sensorPower", p.sensorPower());
            }
            default -> throw new IllegalArgumentException("Unsupported circuit application model: " + model);
        }
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    private static void put(Map<String, List<Double>> values, List<Double> time, String key, double value) {
        values.put(key, Collections.nCopies(time.size(), value));
    }
}
