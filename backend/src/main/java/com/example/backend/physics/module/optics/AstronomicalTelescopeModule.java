package com.example.backend.physics.module.optics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Map;
import java.util.Objects;

/** Typed normal-adjustment astronomical telescope model. */
public final class AstronomicalTelescopeModule implements PhysicsModule<AstronomicalTelescopeModule.Parameters> {
    public static final String MODULE_ID = "astronomical_telescope";
    public static final String NUMERICAL_SOLVER_ID = "astronomical_telescope_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "astronomical_telescope_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "objective_focal_length", "m");
        requireUnit(quantities, "eyepiece_focal_length", "m");
        return new Parameters(quantities.require("objective_focal_length"), quantities.require("eyepiece_focal_length"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        Objects.requireNonNull(clock, "clock");
        double magnification = p.objectiveFocalLength() / p.eyepieceFocalLength();
        double normalAdjustmentLength = p.objectiveFocalLength() + p.eyepieceFocalLength();
        requireFinite(magnification, normalAdjustmentLength);
        return new SolverOutput(clock.sampleTimes(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("angularMagnification", magnification, "signedAngularMagnification", -magnification,
                        "normalAdjustmentLength", normalAdjustmentLength));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        // Reconstruct the angular ratio as the reciprocal of the eyepiece/objective ratio.
        double eyepieceToObjectiveRatio = p.eyepieceFocalLength() / p.objectiveFocalLength();
        double magnification = 1.0 / eyepieceToObjectiveRatio;
        double signedMagnification = -magnification;
        double normalAdjustmentLength = Math.max(p.objectiveFocalLength(), p.eyepieceFocalLength())
                + Math.min(p.objectiveFocalLength(), p.eyepieceFocalLength());
        requireFinite(magnification, signedMagnification, normalAdjustmentLength);
        return new AnalyticalPoint(Map.of("angularMagnification", magnification,
                "signedAngularMagnification", signedMagnification,
                "normalAdjustmentLength", normalAdjustmentLength));
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String expected) {
        if (!expected.equals(values.unit(key))) throw new IllegalArgumentException("Telescope " + key + " must use " + expected);
    }

    private static void requireCheckpoint(double time) {
        if (!Double.isFinite(time) || time < 0.0) throw new IllegalArgumentException("Telescope reference time is invalid");
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Telescope output is not finite");
    }

    public record Parameters(double objectiveFocalLength, double eyepieceFocalLength) {
        public Parameters {
            if (!Double.isFinite(objectiveFocalLength) || objectiveFocalLength <= 0.0
                    || !Double.isFinite(eyepieceFocalLength) || eyepieceFocalLength <= 0.0) {
                throw new IllegalArgumentException("Telescope focal lengths must be finite and positive");
            }
        }
    }
}
