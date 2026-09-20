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

/** Typed apparent-angular-size and totality model for eclipse geometry. */
public final class EclipseGeometryModule implements PhysicsModule<EclipseGeometryModule.Parameters> {
    public static final String MODULE_ID = "eclipse_geometry";
    public static final String NUMERICAL_SOLVER_ID = "eclipse_geometry_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "eclipse_geometry_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(requireCanonical(quantities, "star_radius", "m"),
                requireCanonical(quantities, "star_distance", "m"),
                requireCanonical(quantities, "occluder_radius", "m"),
                requireCanonical(quantities, "occluder_distance", "m"),
                requireCanonical(quantities, "alignment_angle", "rad"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double starDiameter = 2.0 * Math.atan(parameters.starRadius() / parameters.starDistance());
        double occluderDiameter = 2.0 * Math.atan(parameters.occluderRadius() / parameters.occluderDistance());
        double margin = occluderDiameter - starDiameter - parameters.alignmentAngle();
        double totality = margin >= 0.0 ? 1.0 : 0.0;
        requireFinite("starAngularDiameter", starDiameter);
        requireFinite("occluderAngularDiameter", occluderDiameter);
        requireFinite("alignmentMargin", margin);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("starAngularDiameter", repeated(starDiameter, time.size()));
        values.put("occluderAngularDiameter", repeated(occluderDiameter, time.size()));
        values.put("alignmentMargin", repeated(margin, time.size()));
        values.put("totality", repeated(totality, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Eclipse reference time must be finite and non-negative");
        }
        // Use the two-argument angle function as an independently evaluated
        // right-triangle relation; it stays well-scaled for large distances.
        double starDiameter = 2.0 * Math.atan2(parameters.starRadius(), parameters.starDistance());
        double occluderDiameter = 2.0 * Math.atan2(parameters.occluderRadius(), parameters.occluderDistance());
        double margin = occluderDiameter - starDiameter - parameters.alignmentAngle();
        requireFinite("reference starAngularDiameter", starDiameter);
        requireFinite("reference occluderAngularDiameter", occluderDiameter);
        requireFinite("reference alignmentMargin", margin);
        return new AnalyticalPoint(Map.of("starAngularDiameter", starDiameter,
                "occluderAngularDiameter", occluderDiameter, "alignmentMargin", margin,
                "totality", margin >= 0.0 ? 1.0 : 0.0));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical eclipse quantity " + key + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Eclipse quantity must be finite: " + key);
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Eclipse output must be finite: " + key);
    }

    /** Canonical geometric radii, distances and angular misalignment. */
    public record Parameters(double starRadius, double starDistance,
                             double occluderRadius, double occluderDistance,
                             double alignmentAngle) {
        public Parameters {
            if (!Double.isFinite(starRadius) || starRadius <= 0.0
                    || !Double.isFinite(starDistance) || starDistance <= starRadius
                    || !Double.isFinite(occluderRadius) || occluderRadius <= 0.0
                    || !Double.isFinite(occluderDistance) || occluderDistance <= occluderRadius
                    || !Double.isFinite(alignmentAngle) || alignmentAngle < 0.0
                    || alignmentAngle > Math.PI) {
                throw new IllegalArgumentException("Eclipse radii/distances and alignment angle are outside the geometric domain");
            }
        }
    }
}
