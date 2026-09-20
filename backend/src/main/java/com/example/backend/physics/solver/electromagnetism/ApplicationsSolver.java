package com.example.backend.physics.solver.electromagnetism;

import com.example.backend.physics.solver.thermal.TemperatureScaleSolver;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.*;
import com.example.backend.physics.model.circuits.*;
import com.example.backend.physics.model.modern.*;
import com.example.backend.physics.model.electromagnetism.*;
import com.example.backend.physics.model.dynamics.*;
import com.example.backend.physics.model.optics.*;
import com.example.backend.physics.model.thermal.*;
import com.example.backend.physics.model.waves.*;
import com.example.backend.physics.model.practical.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Closed-form communication, semiconductor and medical-ultrasound families. */
@Component
public class ApplicationsSolver implements PhysicsSolver {
    @Override public String solverId() { return "applications_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        List<Double> time = TemperatureScaleSolver.staticTime(durationSeconds, stepSeconds);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        switch (model) {
            case "radio_communication" -> {
                RadioCommunicationParameters p = RadioCommunicationParameters.from(specification, overrides);
                put(values, time, "wavelength", p.wavelength()); put(values, time, "period", p.period());
                put(values, time, "angularFrequency", p.angularFrequency()); put(values, time, "lowerSideband", p.lowerSideband()); put(values, time, "upperSideband", p.upperSideband());
            }
            case "diode_characteristic" -> {
                DiodeParameters p = DiodeParameters.from(specification, overrides);
                put(values, time, "thermalVoltage", p.thermalVoltage()); put(values, time, "current", p.current()); put(values, time, "power", p.power());
            }
            case "ultrasound_imaging" -> {
                UltrasoundImagingParameters p = UltrasoundImagingParameters.from(specification, overrides);
                put(values, time, "wavelength", p.wavelength()); put(values, time, "depth", p.depth()); put(values, time, "period", p.period());
            }
            case "sensor_op_amp" -> {
                SensorOpAmpParameters p = SensorOpAmpParameters.from(specification, overrides);
                put(values, time, "sensorVoltage", p.sensorVoltage()); put(values, time, "referenceVoltage", p.referenceVoltage());
                put(values, time, "amplifiedOutput", p.amplifiedOutput()); put(values, time, "ledState", p.ledState());
                put(values, time, "sensorPower", p.sensorPower());
            }
            case "radio_signal_chain" -> {
                RadioSignalChainParameters p = RadioSignalChainParameters.from(specification, overrides);
                put(values, time, "wavelength", p.wavelength()); put(values, time, "lowerSideband", p.lowerSideband());
                put(values, time, "upperSideband", p.upperSideband()); put(values, time, "fmModulationIndex", p.fmModulationIndex());
                put(values, time, "carsonBandwidth", p.carsonBandwidth()); put(values, time, "attenuationFactor", p.attenuationFactor());
                put(values, time, "receivedAmplitude", p.receivedAmplitude());
            }
            case "eclipse_geometry" -> {
                EclipseGeometryParameters p = EclipseGeometryParameters.from(specification, overrides);
                put(values, time, "starAngularDiameter", p.starAngularDiameter()); put(values, time, "occluderAngularDiameter", p.occluderAngularDiameter());
                put(values, time, "alignmentMargin", p.alignmentMargin()); put(values, time, "totality", p.totality());
            }
            case "energy_environment" -> {
                EnergyEnvironmentParameters p = EnergyEnvironmentParameters.from(specification, overrides);
                put(values, time, "renewableEnergy", p.renewableEnergy()); put(values, time, "fossilEnergy", p.fossilEnergy());
                put(values, time, "emissions", p.emissions()); put(values, time, "usefulEnergy", p.usefulEnergy());
                put(values, time, "avoidedEmissionsVsFossil", p.avoidedEmissionsVsFossil());
            }
            case "thermistor_response" -> {
                ThermistorParameters p = ThermistorParameters.from(specification, overrides);
                put(values, time, "resistance", p.resistance()); put(values, time, "dividerVoltage", p.dividerVoltage()); put(values, time, "sensorPower", p.sensorPower());
            }
            default -> throw new IllegalArgumentException("Unsupported applications model: " + model);
        }
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
    private static void put(Map<String, List<Double>> values, List<Double> time, String key, double value) {
        values.put(key, java.util.Collections.nCopies(time.size(), value));
    }
}
