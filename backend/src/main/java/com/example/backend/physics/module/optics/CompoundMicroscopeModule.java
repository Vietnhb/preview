package com.example.backend.physics.module.optics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Map;
import java.util.Objects;

/** Typed paraxial compound microscope for relaxed and near-point viewing. */
public final class CompoundMicroscopeModule implements PhysicsModule<CompoundMicroscopeModule.Parameters> {
    public static final String MODULE_ID = "compound_microscope";
    public static final String NUMERICAL_SOLVER_ID = "compound_microscope_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "compound_microscope_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "objective_focal_length", "m");
        requireUnit(quantities, "eyepiece_focal_length", "m");
        requireUnit(quantities, "tube_length", "m");
        requireUnit(quantities, "near_point", "m");
        return new Parameters(quantities.require("objective_focal_length"),
                quantities.require("eyepiece_focal_length"), quantities.require("tube_length"),
                quantities.require("near_point"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        Objects.requireNonNull(clock, "clock");
        double objectiveMagnification = p.tubeLength() / p.objectiveFocalLength();
        double relaxedAngularMagnification = objectiveMagnification * (p.nearPoint() / p.eyepieceFocalLength());
        double nearPointAngularMagnification = objectiveMagnification
                * (1.0 + p.nearPoint() / p.eyepieceFocalLength());
        requireFinite(objectiveMagnification, relaxedAngularMagnification, nearPointAngularMagnification);
        return new SolverOutput(clock.sampleTimes(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("objectiveMagnification", objectiveMagnification,
                        "relaxedAngularMagnification", relaxedAngularMagnification,
                        "nearPointAngularMagnification", nearPointAngularMagnification));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        // Interpret the objective as an image-height ratio and the eyepiece as a separate angular ratio.
        double objectiveMagnification = (p.tubeLength() * p.eyepieceFocalLength())
                / (p.objectiveFocalLength() * p.eyepieceFocalLength());
        double relaxedAngularMagnification = (p.tubeLength() * p.nearPoint())
                / (p.objectiveFocalLength() * p.eyepieceFocalLength());
        double nearPointAngularMagnification = objectiveMagnification
                + relaxedAngularMagnification;
        requireFinite(objectiveMagnification, relaxedAngularMagnification, nearPointAngularMagnification);
        return new AnalyticalPoint(Map.of("objectiveMagnification", objectiveMagnification,
                "relaxedAngularMagnification", relaxedAngularMagnification,
                "nearPointAngularMagnification", nearPointAngularMagnification));
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String expected) {
        if (!expected.equals(values.unit(key))) throw new IllegalArgumentException("Microscope " + key + " must use " + expected);
    }

    private static void requireCheckpoint(double time) {
        if (!Double.isFinite(time) || time < 0.0) throw new IllegalArgumentException("Microscope reference time is invalid");
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Microscope output is not finite");
    }

    public record Parameters(double objectiveFocalLength, double eyepieceFocalLength,
                             double tubeLength, double nearPoint) {
        public Parameters {
            if (!Double.isFinite(objectiveFocalLength) || objectiveFocalLength <= 0.0
                    || !Double.isFinite(eyepieceFocalLength) || eyepieceFocalLength <= 0.0
                    || !Double.isFinite(tubeLength) || tubeLength <= 0.0
                    || !Double.isFinite(nearPoint) || nearPoint <= 0.0) {
                throw new IllegalArgumentException("Microscope focal lengths, tube length, and near point must be positive");
            }
        }
    }
}
