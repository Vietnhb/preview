package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed Beer-Lambert model for a diagnostic X-ray beam. */
public final class XrayImagingModule implements PhysicsModule<XrayImagingModule.Parameters> {
    public static final String MODULE_ID = "xray_imaging";
    public static final String NUMERICAL_SOLVER_ID = "xray_imaging_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "xray_imaging_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "incident_intensity", "1"),
                requireCanonical(quantities, "attenuation_coefficient", "1/m"),
                requireCanonical(quantities, "material_thickness", "m"),
                requireCanonical(quantities, "exposure_time", "s"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double opticalDepth = parameters.attenuationCoefficient() * parameters.materialThickness();
        double transmitted = parameters.incidentIntensity() * Math.exp(-opticalDepth);
        double absorbed = -Math.expm1(-opticalDepth);
        double dose = transmitted * parameters.exposureTime();
        double halfValueLayer = Math.log(2.0) / parameters.attenuationCoefficient();
        requireFinite("transmittedIntensity", transmitted);
        requireFinite("absorbedFraction", absorbed);
        requireFinite("detectorDoseProxy", dose);
        requireFinite("halfValueLayer", halfValueLayer);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("transmittedIntensity", repeated(transmitted, time.size()));
        values.put("absorbedFraction", repeated(absorbed, time.size()));
        values.put("detectorDoseProxy", repeated(dose, time.size()));
        values.put("halfValueLayer", repeated(halfValueLayer, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireReferenceTime(timeSeconds);
        double logTransmission = Math.log(parameters.incidentIntensity())
                - parameters.attenuationCoefficient() * parameters.materialThickness();
        double transmitted = Math.exp(logTransmission);
        double absorbed = 1.0 - transmitted / parameters.incidentIntensity();
        double dose = Math.exp(logTransmission + Math.log(parameters.exposureTime()));
        // Solve I(x_1/2) / I(0) = 1/2 from the attenuation relation.
        double halfValueLayer = -Math.log(0.5) / parameters.attenuationCoefficient();
        requireFinite("reference transmittedIntensity", transmitted);
        requireFinite("reference absorbedFraction", absorbed);
        requireFinite("reference detectorDoseProxy", dose);
        requireFinite("reference halfValueLayer", halfValueLayer);
        return new AnalyticalPoint(Map.of("transmittedIntensity", transmitted,
                "absorbedFraction", absorbed, "detectorDoseProxy", dose,
                "halfValueLayer", halfValueLayer));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical X-ray quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("X-ray quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        List<Double> output = new ArrayList<>(count);
        for (int index = 0; index < count; index++) output.add(value);
        return List.copyOf(output);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("X-ray output must be finite: " + key);
    }

    private static void requireReferenceTime(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("X-ray reference time must be finite and non-negative");
        }
    }

    /** Canonical ingress values in SI-compatible units. */
    public record Parameters(double incidentIntensity, double attenuationCoefficient,
                             double materialThickness, double exposureTime) {
        public Parameters {
            if (!Double.isFinite(incidentIntensity) || incidentIntensity <= 0.0
                    || !Double.isFinite(attenuationCoefficient) || attenuationCoefficient <= 0.0
                    || !Double.isFinite(materialThickness) || materialThickness < 0.0
                    || !Double.isFinite(exposureTime) || exposureTime <= 0.0) {
                throw new IllegalArgumentException("X-ray intensity, attenuation, thickness and exposure are outside the physical domain");
            }
        }
    }
}
