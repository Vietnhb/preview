package com.example.backend.physics.module.electromagnetism;

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

/** Typed Faraday induction for a coil in a spatially uniform, linearly changing field. */
public final class ElectromagneticInductionModule
        implements PhysicsModule<ElectromagneticInductionModule.Parameters> {
    public static final String MODULE_ID = "electromagnetic_induction";
    public static final String NUMERICAL_SOLVER_ID = "electromagnetic_induction_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "electromagnetic_induction_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "turns", "1");
        requireUnit(quantities, "magnetic_field", "T");
        requireUnit(quantities, "coil_area", "m2");
        requireUnit(quantities, "magnetic_field_rate", "T/s");
        requireUnit(quantities, "coil_angle", "rad");
        return new Parameters(quantities.require("turns"), quantities.require("magnetic_field"),
                quantities.require("coil_area"), quantities.require("magnetic_field_rate"),
                quantities.require("coil_angle"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        List<Double> times = Objects.requireNonNull(clock, "clock").sampleTimes();
        double projectedArea = p.coilArea() * cosineForProjection(p.coilAngle());
        List<Double> field = new ArrayList<>(times.size());
        List<Double> flux = new ArrayList<>(times.size());
        for (double time : times) {
            double fieldAtTime = p.initialMagneticField() + p.magneticFieldRate() * time;
            double fluxAtTime = fieldAtTime * projectedArea;
            requireFinite(fieldAtTime, fluxAtTime);
            field.add(fieldAtTime);
            flux.add(fluxAtTime);
        }
        double inducedEmf = -p.turns() * projectedArea * p.magneticFieldRate();
        requireFinite(inducedEmf);
        Map<String, List<Double>> series = new LinkedHashMap<>();
        series.put("magneticField", List.copyOf(field));
        series.put("magneticFlux", List.copyOf(flux));
        return new SolverOutput(times, Map.of(), Map.of(), Map.of(), series, Map.of(), Map.of("inducedEmf", inducedEmf));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double field = p.initialMagneticField() + p.magneticFieldRate() * timeSeconds;
        double normalComponent = field * Math.cos(p.coilAngle());
        double flux = p.coilArea() * normalComponent;
        double perTurnRate = (p.coilArea() * p.magneticFieldRate()) * Math.cos(p.coilAngle());
        double inducedEmf = -p.turns() * perTurnRate;
        requireFinite(field, flux, inducedEmf);
        return new AnalyticalPoint(Map.of("magneticField", field, "magneticFlux", flux, "inducedEmf", inducedEmf));
    }

    private static double cosineForProjection(double angle) {
        if (angle == 0.0) return 1.0;
        if (angle == Math.PI / 2.0) return 0.0;
        if (angle == Math.PI) return -1.0;
        return Math.cos(angle);
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String expected) {
        if (!expected.equals(values.unit(key))) throw new IllegalArgumentException("Induction " + key + " must use " + expected);
    }

    private static void requireCheckpoint(double time) {
        if (!Double.isFinite(time) || time < 0.0) throw new IllegalArgumentException("Induction reference time is invalid");
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Induction output is not finite");
    }

    public record Parameters(double turns, double initialMagneticField, double coilArea,
                             double magneticFieldRate, double coilAngle) {
        public Parameters {
            if (!Double.isFinite(turns) || turns < 1.0 || turns != Math.rint(turns)
                    || !Double.isFinite(initialMagneticField) || !Double.isFinite(coilArea) || coilArea <= 0.0
                    || !Double.isFinite(magneticFieldRate) || !Double.isFinite(coilAngle)
                    || coilAngle < 0.0 || coilAngle > Math.PI) {
                throw new IllegalArgumentException("Induction inputs must be finite; turns, area, and angle must be physical");
            }
        }
    }
}
