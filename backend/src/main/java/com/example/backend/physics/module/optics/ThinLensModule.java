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

/** Typed paraxial thin-lens imaging module for the published optics binding. */
public final class ThinLensModule implements PhysicsModule<ThinLensModule.Parameters> {
    public static final String MODULE_ID = "thin_lens_imaging";
    public static final String NUMERICAL_SOLVER_ID = "thin_lens_solver";
    public static final String REFERENCE_SOLVER_ID = "thin_lens_reference";
    private static final String IMAGE_DISTANCE = "imageDistance";
    private static final String MAGNIFICATION = "magnification";
    private static final String IMAGE_HEIGHT = "imageHeight";
    private static final String REFERENCE_PREFIX = "reference ";

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
        requireUnit(quantities, "focal_length", "m");
        requireUnit(quantities, "object_distance", "m");
        requireUnit(quantities, "object_height", "m");
        return new Parameters(quantities.require("focal_length"),
                quantities.require("object_distance"), quantities.require("object_height"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        if (parameters == null || clock == null) throw new IllegalArgumentException("Lens parameters and clock are required");
        double imageDistance = parameters.focalLength() * parameters.objectDistance()
                / (parameters.objectDistance() - parameters.focalLength());
        double magnification = -imageDistance / parameters.objectDistance();
        double imageHeight = magnification * parameters.objectHeight();
        requireFinite(imageDistance, IMAGE_DISTANCE);
        requireFinite(magnification, MAGNIFICATION);
        requireFinite(imageHeight, IMAGE_HEIGHT);

        List<Double> time = clock.sampleTimes();
        List<Double> imageDistances = repeated(imageDistance, time.size());
        List<Double> imageHeights = repeated(imageHeight, time.size());
        List<Double> magnifications = repeated(magnification, time.size());
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(IMAGE_DISTANCE, imageDistances);
        values.put(IMAGE_HEIGHT, imageHeights);
        values.put(MAGNIFICATION, magnifications);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        if (parameters == null) throw new IllegalArgumentException("Lens parameters are required");
        requireCheckpoint(timeSeconds);
        // Independent scalar oracle: recompute the imaging relation directly from
        // the primitive bound values rather than reusing the numerical result path.
        double reciprocalImageDistance = 1.0 / parameters.focalLength()
                - 1.0 / parameters.objectDistance();
        if (reciprocalImageDistance == 0.0 || !Double.isFinite(reciprocalImageDistance)) {
            throw new IllegalArgumentException("Thin-lens reference image distance is not finite");
        }
        double imageDistance = 1.0 / reciprocalImageDistance;
        double lateralMagnification = -imageDistance / parameters.objectDistance();
        double imageHeight = lateralMagnification * parameters.objectHeight();
        requireFinite(imageDistance, REFERENCE_PREFIX + IMAGE_DISTANCE);
        requireFinite(lateralMagnification, REFERENCE_PREFIX + MAGNIFICATION);
        requireFinite(imageHeight, REFERENCE_PREFIX + IMAGE_HEIGHT);
        return new AnalyticalPoint(Map.of(
                IMAGE_DISTANCE, imageDistance,
                IMAGE_HEIGHT, imageHeight,
                MAGNIFICATION, lateralMagnification));
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
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Thin-lens " + output + " must be finite");
    }

    private static void requireCheckpoint(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }
    }

    /** Immutable primitive parameters compiled only from canonical quantities. */
    public record Parameters(double focalLength, double objectDistance, double objectHeight) {
        public Parameters {
            if (!Double.isFinite(focalLength) || focalLength == 0.0) {
                throw new IllegalArgumentException("Lens focal length must be finite and non-zero");
            }
            if (!Double.isFinite(objectDistance) || objectDistance <= 0.0) {
                throw new IllegalArgumentException("Lens object distance must be finite and positive");
            }
            if (!Double.isFinite(objectHeight)) {
                throw new IllegalArgumentException("Lens object height must be finite");
            }
            if (Math.abs(objectDistance - focalLength) < 1.0e-12) {
                throw new IllegalArgumentException("Object at the focal plane has no finite thin-lens image");
            }
        }
    }
}
