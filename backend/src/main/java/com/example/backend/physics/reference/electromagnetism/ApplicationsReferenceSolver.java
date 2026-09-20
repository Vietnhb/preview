package com.example.backend.physics.reference.electromagnetism;

import com.example.backend.physics.reference.ReferenceSolver;

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
import java.util.Map;

/** Independent oracle for application models. */
@Component
public class ApplicationsReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "applications_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        String model = PhysicsValues.model(specification); Map<String, Double> values = new LinkedHashMap<>();
        switch (model) {
            case "radio_communication" -> { RadioCommunicationParameters p = RadioCommunicationParameters.from(specification, overrides); values.put("wavelength", p.wavelength()); values.put("period", p.period()); values.put("angularFrequency", p.angularFrequency()); values.put("lowerSideband", p.lowerSideband()); values.put("upperSideband", p.upperSideband()); }
            case "diode_characteristic" -> { DiodeParameters p = DiodeParameters.from(specification, overrides); values.put("thermalVoltage", p.thermalVoltage()); values.put("current", p.current()); values.put("power", p.power()); }
            case "ultrasound_imaging" -> { UltrasoundImagingParameters p = UltrasoundImagingParameters.from(specification, overrides); values.put("wavelength", p.wavelength()); values.put("depth", p.depth()); values.put("period", p.period()); }
            case "sensor_op_amp" -> { SensorOpAmpParameters p = SensorOpAmpParameters.from(specification, overrides); values.put("sensorVoltage", p.sensorVoltage()); values.put("referenceVoltage", p.referenceVoltage()); values.put("amplifiedOutput", p.amplifiedOutput()); values.put("ledState", p.ledState()); values.put("sensorPower", p.sensorPower()); }
            case "radio_signal_chain" -> { RadioSignalChainParameters p = RadioSignalChainParameters.from(specification, overrides); values.put("wavelength", p.wavelength()); values.put("lowerSideband", p.lowerSideband()); values.put("upperSideband", p.upperSideband()); values.put("fmModulationIndex", p.fmModulationIndex()); values.put("carsonBandwidth", p.carsonBandwidth()); values.put("attenuationFactor", p.attenuationFactor()); values.put("receivedAmplitude", p.receivedAmplitude()); }
            case "eclipse_geometry" -> { EclipseGeometryParameters p = EclipseGeometryParameters.from(specification, overrides); values.put("starAngularDiameter", p.starAngularDiameter()); values.put("occluderAngularDiameter", p.occluderAngularDiameter()); values.put("alignmentMargin", p.alignmentMargin()); values.put("totality", p.totality()); }
            case "energy_environment" -> { EnergyEnvironmentParameters p = EnergyEnvironmentParameters.from(specification, overrides); values.put("renewableEnergy", p.renewableEnergy()); values.put("fossilEnergy", p.fossilEnergy()); values.put("emissions", p.emissions()); values.put("usefulEnergy", p.usefulEnergy()); values.put("avoidedEmissionsVsFossil", p.avoidedEmissionsVsFossil()); }
            case "thermistor_response" -> { ThermistorParameters p = ThermistorParameters.from(specification, overrides); values.put("resistance", p.resistance()); values.put("dividerVoltage", p.dividerVoltage()); values.put("sensorPower", p.sensorPower()); }
            default -> throw new IllegalArgumentException("Unsupported applications model: " + model);
        }
        return new AnalyticalPoint(values);
    }
}
