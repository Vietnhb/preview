package com.example.backend.physics.compatibility.legacy.solver.medical;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.medical.CtReconstructionParameters;
import com.example.backend.physics.compatibility.legacy.model.medical.MriRelaxationParameters;
import com.example.backend.physics.compatibility.legacy.model.medical.XrayImagingParameters;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diagnostic-imaging teaching models from specialised topic 12.2. */
@Component
public class MedicalImagingSolver implements PhysicsSolver {
    @Override public String solverId() { return "medical_imaging_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        if (!(durationSeconds > 0) || !(stepSeconds > 0) || !Double.isFinite(durationSeconds + stepSeconds)) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(durationSeconds / stepSeconds)));
        List<Double> time = new ArrayList<>(points + 1);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        switch (model) {
            case "xray_imaging" -> {
                XrayImagingParameters p = XrayImagingParameters.from(specification, overrides);
                put(values, time, points, durationSeconds, stepSeconds, t -> p.transmittedIntensity(),
                        "transmittedIntensity");
                putConstant(values, time.size(), "absorbedFraction", p.absorbedFraction());
                putConstant(values, time.size(), "detectorDoseProxy", p.detectorDoseProxy());
                putConstant(values, time.size(), "halfValueLayer", p.halfValueLayer());
            }
            case "ct_reconstruction" -> {
                CtReconstructionParameters p = CtReconstructionParameters.from(specification, overrides);
                put(values, time, points, durationSeconds, stepSeconds,
                        t -> p.transmittedIntensity(), "transmittedIntensity");
                putConstant(values, time.size(), "lineIntegral", p.lineIntegral());
                putConstant(values, time.size(), "angularStep", p.angularStep());
                putConstant(values, time.size(), "reconstructedAttenuation", p.attenuationCoefficient());
            }
            case "mri_relaxation" -> {
                MriRelaxationParameters p = MriRelaxationParameters.from(specification, overrides);
                put(values, time, points, durationSeconds, stepSeconds,
                        p::longitudinalMagnetization, "longitudinalMagnetization");
                List<Double> transverse = values.computeIfAbsent("transverseMagnetization", ignored -> new ArrayList<>());
                for (int i = 0; i <= points; i++) {
                    double t = Math.min(durationSeconds, i * stepSeconds);
                    transverse.add(p.transverseMagnetization(t));
                }
                putConstant(values, time.size(), "echoSignal", p.echoSignal());
            }
            default -> throw new IllegalArgumentException("Unsupported medical imaging model: " + model);
        }
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    private interface ValueAtTime { double value(double time); }

    private static void put(Map<String, List<Double>> values, List<Double> time, int points,
                            double duration, double step, ValueAtTime function, String key) {
        List<Double> output = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) {
            double t = Math.min(duration, i * step);
            time.add(t);
            output.add(function.value(t));
        }
        values.put(key, output);
    }

    private static void putConstant(Map<String, List<Double>> values, int count, String key, double value) {
        values.put(key, java.util.Collections.nCopies(count, value));
    }
}
