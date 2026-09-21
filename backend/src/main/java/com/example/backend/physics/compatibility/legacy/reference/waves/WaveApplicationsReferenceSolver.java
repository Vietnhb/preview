package com.example.backend.physics.compatibility.legacy.reference.waves;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.waves.RadioCommunicationParameters;
import com.example.backend.physics.compatibility.legacy.model.waves.RadioSignalChainParameters;
import com.example.backend.physics.compatibility.legacy.model.waves.UltrasoundImagingParameters;
import com.example.backend.physics.compatibility.legacy.reference.TopicReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Wave/communications application reference oracle. */
@Component
public class WaveApplicationsReferenceSolver implements TopicReferenceSolver {
    @Override public String solverId() { return "wave_applications_reference"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("radio_communication", "radio_signal_chain", "ultrasound_imaging"); }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double time) {
        String model = PhysicsValues.model(specification); Map<String, Double> values = new LinkedHashMap<>();
        switch (model) {
            case "radio_communication" -> { RadioCommunicationParameters p = RadioCommunicationParameters.from(specification, overrides); values.put("wavelength", p.wavelength()); values.put("period", p.period()); values.put("angularFrequency", p.angularFrequency()); values.put("lowerSideband", p.lowerSideband()); values.put("upperSideband", p.upperSideband()); }
            case "radio_signal_chain" -> { RadioSignalChainParameters p = RadioSignalChainParameters.from(specification, overrides); values.put("wavelength", p.wavelength()); values.put("lowerSideband", p.lowerSideband()); values.put("upperSideband", p.upperSideband()); values.put("fmModulationIndex", p.fmModulationIndex()); values.put("carsonBandwidth", p.carsonBandwidth()); values.put("attenuationFactor", p.attenuationFactor()); values.put("receivedAmplitude", p.receivedAmplitude()); }
            case "ultrasound_imaging" -> { UltrasoundImagingParameters p = UltrasoundImagingParameters.from(specification, overrides); values.put("wavelength", p.wavelength()); values.put("depth", p.depth()); values.put("period", p.period()); }
            default -> throw new IllegalArgumentException("Unsupported wave application model: " + model);
        }
        return new AnalyticalPoint(values);
    }
}
