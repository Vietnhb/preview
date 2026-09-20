package com.example.backend.physics.reference.medical;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.medical.CtReconstructionParameters;
import com.example.backend.physics.model.medical.MriRelaxationParameters;
import com.example.backend.physics.model.medical.XrayImagingParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Independent closed-form oracle for X-ray, CT projection and MRI relaxation. */
@Component
public class MedicalImagingReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "medical_imaging_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        String model = PhysicsValues.model(specification);
        Map<String, Double> values = new LinkedHashMap<>();
        switch (model) {
            case "xray_imaging" -> {
                XrayImagingParameters p = XrayImagingParameters.from(specification, overrides);
                values.put("transmittedIntensity", p.transmittedIntensity());
                values.put("absorbedFraction", p.absorbedFraction());
                values.put("detectorDoseProxy", p.detectorDoseProxy());
                values.put("halfValueLayer", p.halfValueLayer());
            }
            case "ct_reconstruction" -> {
                CtReconstructionParameters p = CtReconstructionParameters.from(specification, overrides);
                values.put("transmittedIntensity", p.transmittedIntensity());
                values.put("lineIntegral", p.lineIntegral());
                values.put("angularStep", p.angularStep());
                values.put("reconstructedAttenuation", p.attenuationCoefficient());
            }
            case "mri_relaxation" -> {
                MriRelaxationParameters p = MriRelaxationParameters.from(specification, overrides);
                values.put("longitudinalMagnetization", p.longitudinalMagnetization(timeSeconds));
                values.put("transverseMagnetization", p.transverseMagnetization(timeSeconds));
                values.put("echoSignal", p.echoSignal());
            }
            default -> throw new IllegalArgumentException("Unsupported medical imaging model: " + model);
        }
        return new AnalyticalPoint(values);
    }
}
