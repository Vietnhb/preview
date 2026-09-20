package com.example.backend.physics.module.optics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Map;
import java.util.Objects;

/** Typed virtual-image simple magnifier model. */
public final class SimpleMagnifierModule implements PhysicsModule<SimpleMagnifierModule.Parameters> {
    public static final String MODULE_ID = "simple_magnifier";
    public static final String NUMERICAL_SOLVER_ID = "simple_magnifier_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "simple_magnifier_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "focal_length", "m");
        requireUnit(quantities, "object_distance", "m");
        requireUnit(quantities, "near_point", "m");
        return new Parameters(quantities.require("focal_length"), quantities.require("object_distance"),
                quantities.require("near_point"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        Objects.requireNonNull(clock, "clock");
        double denominator = p.focalLength() - p.objectDistance();
        double virtualImageDistance = p.objectDistance() / (denominator / p.focalLength());
        double linearMagnification = 1.0 / (1.0 - p.objectDistance() / p.focalLength());
        double relaxedAngularMagnification = p.nearPoint() / p.focalLength();
        double nearPointAngularMagnification = 1.0 + relaxedAngularMagnification;
        requireFinite(virtualImageDistance, linearMagnification, relaxedAngularMagnification, nearPointAngularMagnification);
        return new SolverOutput(clock.sampleTimes(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("virtualImageDistance", virtualImageDistance, "linearMagnification", linearMagnification,
                        "relaxedAngularMagnification", relaxedAngularMagnification,
                        "nearPointAngularMagnification", nearPointAngularMagnification));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        // Thin-lens equation written in reciprocal distances, independently of the solver's focal-distance ratio.
        double reciprocalImageDistance = (1.0 / p.objectDistance()) - (1.0 / p.focalLength());
        double virtualImageDistance = 1.0 / reciprocalImageDistance;
        double linearMagnification = virtualImageDistance / p.objectDistance();
        double relaxedAngularMagnification = p.nearPoint() * (1.0 / p.focalLength());
        double nearPointAngularMagnification = (p.focalLength() + p.nearPoint()) / p.focalLength();
        requireFinite(virtualImageDistance, linearMagnification, relaxedAngularMagnification, nearPointAngularMagnification);
        return new AnalyticalPoint(Map.of("virtualImageDistance", virtualImageDistance,
                "linearMagnification", linearMagnification,
                "relaxedAngularMagnification", relaxedAngularMagnification,
                "nearPointAngularMagnification", nearPointAngularMagnification));
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String expected) {
        if (!expected.equals(values.unit(key))) throw new IllegalArgumentException("Magnifier " + key + " must use " + expected);
    }

    private static void requireCheckpoint(double time) {
        if (!Double.isFinite(time) || time < 0.0) throw new IllegalArgumentException("Magnifier reference time is invalid");
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Magnifier output is not finite");
    }

    public record Parameters(double focalLength, double objectDistance, double nearPoint) {
        public Parameters {
            if (!Double.isFinite(focalLength) || focalLength <= 0.0
                    || !Double.isFinite(objectDistance) || objectDistance <= 0.0 || objectDistance >= focalLength
                    || !Double.isFinite(nearPoint) || nearPoint <= 0.0) {
                throw new IllegalArgumentException("Magnifier requires 0 < object distance < focal length and positive near point");
            }
        }
    }
}
