package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed single-ray CT projection and reconstruction teaching model. */
public final class CtReconstructionModule implements PhysicsModule<CtReconstructionModule.Parameters> {
    public static final String MODULE_ID = "ct_reconstruction";
    public static final String NUMERICAL_SOLVER_ID = "ct_reconstruction_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "ct_reconstruction_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double rawProjectionCount = requireCanonical(quantities, "projection_count", "1");
        if (rawProjectionCount != Math.rint(rawProjectionCount)
                || rawProjectionCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("CT projection_count must be a positive integer");
        }
        return new Parameters(requireCanonical(quantities, "incident_intensity", "1"),
                requireCanonical(quantities, "attenuation_coefficient", "1/m"),
                requireCanonical(quantities, "path_length", "m"), (int) rawProjectionCount);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double lineIntegral = parameters.attenuationCoefficient() * parameters.pathLength();
        double transmitted = parameters.incidentIntensity() * Math.exp(-lineIntegral);
        double angularStep = 2.0 * Math.PI / parameters.projectionCount();
        requireFinite("transmittedIntensity", transmitted);
        requireFinite("lineIntegral", lineIntegral);
        requireFinite("angularStep", angularStep);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("transmittedIntensity", repeated(transmitted, time.size()));
        values.put("lineIntegral", repeated(lineIntegral, time.size()));
        values.put("angularStep", repeated(angularStep, time.size()));
        values.put("reconstructedAttenuation", repeated(parameters.attenuationCoefficient(), time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("CT reference time must be finite and non-negative");
        }
        double transmitted = Math.exp(Math.log(parameters.incidentIntensity())
                - parameters.attenuationCoefficient() * parameters.pathLength());
        double logProjection = Math.log(parameters.incidentIntensity()) - Math.log(transmitted);
        double angle = 2.0 * Math.PI / (double) parameters.projectionCount();
        requireFinite("reference transmittedIntensity", transmitted);
        requireFinite("reference lineIntegral", logProjection);
        requireFinite("reference angularStep", angle);
        return new AnalyticalPoint(Map.of("transmittedIntensity", transmitted,
                "lineIntegral", logProjection, "angularStep", angle,
                "reconstructedAttenuation", parameters.attenuationCoefficient()));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical CT quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("CT quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("CT output must be finite: " + key);
    }

    /** Canonical line-integral inputs and validated positive projection count. */
    public record Parameters(double incidentIntensity, double attenuationCoefficient,
                             double pathLength, int projectionCount) {
        public Parameters {
            if (!Double.isFinite(incidentIntensity) || incidentIntensity <= 0.0
                    || !Double.isFinite(attenuationCoefficient) || attenuationCoefficient < 0.0
                    || !Double.isFinite(pathLength) || pathLength <= 0.0 || projectionCount <= 0) {
                throw new IllegalArgumentException("CT projection parameters are outside the physical domain");
            }
        }
    }
}
