package com.example.backend.physics.module.optics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed Snell-law module, including an explicit total-internal-reflection state. */
public final class SnellRefractionModule implements PhysicsModule<SnellRefractionModule.Parameters> {
    public static final String MODULE_ID = "snell_refraction";
    public static final String NUMERICAL_SOLVER_ID = "refraction_solver";
    public static final String REFERENCE_SOLVER_ID = "refraction_reference";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String numericalSolverId() {
        return NUMERICAL_SOLVER_ID;
    }

    @Override
    public String referenceSolverId() {
        return REFERENCE_SOLVER_ID;
    }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) throw new IllegalArgumentException("Canonical quantities are required");
        requireUnit(quantities, "refractive_index_1", "1");
        requireUnit(quantities, "refractive_index_2", "1");
        requireUnit(quantities, "incident_angle", "rad");
        return new Parameters(quantities.require("refractive_index_1"),
                quantities.require("refractive_index_2"), quantities.require("incident_angle"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        if (parameters == null || clock == null) throw new IllegalArgumentException("Refraction parameters and clock are required");
        double transmittedSine = parameters.refractiveIndex1() * Math.sin(parameters.incidentAngle())
                / parameters.refractiveIndex2();
        boolean totalInternalReflection = transmittedSine > 1.0;
        double refractedAngle = totalInternalReflection ? 0.0 : Math.asin(transmittedSine);
        requireFinite(refractedAngle, "refractedAngle");

        List<Double> time = clock.sampleTimes();
        List<Double> refracted = repeated(refractedAngle, time.size());
        List<Double> reflected = repeated(totalInternalReflection ? 1.0 : 0.0, time.size());
        List<Double> incident = repeated(parameters.incidentAngle(), time.size());
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("refractedAngle", refracted);
        values.put("totalInternalReflection", reflected);
        values.put("reflectedAngle", incident);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        if (parameters == null) throw new IllegalArgumentException("Refraction parameters are required");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }
        // This oracle applies Snell's law independently from the numerical series
        // construction above and performs its own TIR classification.
        double incidentSine = Math.sin(parameters.incidentAngle());
        double transmittedSine = (parameters.refractiveIndex1() / parameters.refractiveIndex2()) * incidentSine;
        boolean noTransmittedRay = transmittedSine > 1.0;
        double refractedAngle = noTransmittedRay ? 0.0 : Math.atan2(transmittedSine,
                Math.sqrt(Math.max(0.0, 1.0 - transmittedSine * transmittedSine)));
        requireFinite(refractedAngle, "reference refractedAngle");
        return new AnalyticalPoint(Map.of(
                "refractedAngle", refractedAngle,
                "totalInternalReflection", noTransmittedRay ? 1.0 : 0.0,
                "reflectedAngle", parameters.incidentAngle()));
    }

    private static List<Double> repeated(double value, int count) {
        List<Double> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(value);
        return List.copyOf(values);
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expected) {
        if (!expected.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for " + key + " must be " + expected);
        }
    }

    private static void requireFinite(double value, String output) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Snell refraction " + output + " must be finite");
    }

    /** Immutable inputs canonicalized by the schema ingress before module binding. */
    public record Parameters(double refractiveIndex1, double refractiveIndex2, double incidentAngle) {
        public Parameters {
            if (!Double.isFinite(refractiveIndex1) || refractiveIndex1 <= 0.0
                    || !Double.isFinite(refractiveIndex2) || refractiveIndex2 <= 0.0) {
                throw new IllegalArgumentException("Refractive indices must be finite and positive");
            }
            if (!Double.isFinite(incidentAngle) || incidentAngle < 0.0 || incidentAngle > Math.PI / 2.0) {
                throw new IllegalArgumentException("Incident angle must be finite and in [0, pi/2]");
            }
        }
    }
}
