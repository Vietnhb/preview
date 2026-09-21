package com.example.backend.physics.compatibility.legacy.solver.waves;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.waves.RadioCommunicationParameters;
import com.example.backend.physics.compatibility.legacy.model.waves.RadioSignalChainParameters;
import com.example.backend.physics.compatibility.legacy.model.waves.UltrasoundImagingParameters;
import com.example.backend.physics.runtime.SimulationTimeline;
import com.example.backend.physics.compatibility.legacy.solver.TopicPhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wave/communications application family. */
@Component
public class WaveApplicationsSolver implements TopicPhysicsSolver {
    @Override public String solverId() { return "wave_applications_solver"; }
    @Override public java.util.Set<String> supportedModels() { return java.util.Set.of("radio_communication", "radio_signal_chain", "ultrasound_imaging"); }

    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
            double duration, double step) {
        String model = PhysicsValues.model(specification);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        switch (model) {
            case "radio_communication" -> {
                RadioCommunicationParameters p = RadioCommunicationParameters.from(specification, overrides);
                put(values, time, "wavelength", p.wavelength()); put(values, time, "period", p.period());
                put(values, time, "angularFrequency", p.angularFrequency()); put(values, time, "lowerSideband", p.lowerSideband());
                put(values, time, "upperSideband", p.upperSideband());
            }
            case "radio_signal_chain" -> {
                RadioSignalChainParameters p = RadioSignalChainParameters.from(specification, overrides);
                put(values, time, "wavelength", p.wavelength()); put(values, time, "lowerSideband", p.lowerSideband());
                put(values, time, "upperSideband", p.upperSideband()); put(values, time, "fmModulationIndex", p.fmModulationIndex());
                put(values, time, "carsonBandwidth", p.carsonBandwidth()); put(values, time, "attenuationFactor", p.attenuationFactor());
                put(values, time, "receivedAmplitude", p.receivedAmplitude());
            }
            case "ultrasound_imaging" -> {
                UltrasoundImagingParameters p = UltrasoundImagingParameters.from(specification, overrides);
                put(values, time, "wavelength", p.wavelength()); put(values, time, "depth", p.depth()); put(values, time, "period", p.period());
            }
            default -> throw new IllegalArgumentException("Unsupported wave application model: " + model);
        }
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
    private static void put(Map<String, List<Double>> values, List<Double> time, String key, double value) {
        values.put(key, Collections.nCopies(time.size(), value));
    }
}
