package com.example.backend.physics.module.dynamics;

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

/** Typed planar torque balance for two forces about a pivot. */
public final class MomentEquilibriumModule implements PhysicsModule<MomentEquilibriumModule.Parameters> {
    public static final String MODULE_ID = "moment_equilibrium";
    public static final String NUMERICAL_SOLVER_ID = "moment_equilibrium_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "moment_equilibrium_reference_v2";
    private static final String MOMENT_1 = "moment1";
    private static final String MOMENT_2 = "moment2";
    private static final String NET_MOMENT = "netMoment";
    private static final String EQUILIBRIUM_RESIDUAL = "equilibriumResidual";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "force_1", "N"),
                requireCanonical(quantities, "arm_1", "m"),
                requireCanonical(quantities, "angle_1", "rad"),
                requireCanonical(quantities, "force_2", "N"),
                requireCanonical(quantities, "arm_2", "m"),
                requireCanonical(quantities, "angle_2", "rad"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");

        double moment1 = parameters.force1() * parameters.arm1() * Math.sin(parameters.angle1());
        double moment2 = parameters.force2() * parameters.arm2() * Math.sin(parameters.angle2());
        double netMoment = moment1 + moment2;
        double equilibriumResidual = Math.abs(netMoment);
        requireFiniteResults(moment1, moment2, netMoment, equilibriumResidual);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(MOMENT_1, Collections.singletonList(moment1));
        values.put(MOMENT_2, Collections.singletonList(moment2));
        values.put(NET_MOMENT, Collections.singletonList(netMoment));
        values.put(EQUILIBRIUM_RESIDUAL, Collections.singletonList(equilibriumResidual));
        return new SolverOutput(List.of(0.0), Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Moment-equilibrium reference time must be finite and non-negative");
        }

        // With each lever arm on the x axis, resolve the force vector and use
        // r x F = (r_x F_y - r_y F_x) z-hat. The oracle never evaluates F*r*sin(theta).
        double force1X = parameters.force1() * Math.cos(parameters.angle1());
        double force1Y = parameters.force1() * Math.sin(parameters.angle1());
        double force2X = parameters.force2() * Math.cos(parameters.angle2());
        double force2Y = parameters.force2() * Math.sin(parameters.angle2());
        double moment1 = crossProductZ(parameters.arm1(), 0.0, force1X, force1Y);
        double moment2 = crossProductZ(parameters.arm2(), 0.0, force2X, force2Y);
        double netMoment = moment1 + moment2;
        double equilibriumResidual = Math.abs(netMoment);
        requireFiniteResults(moment1, moment2, netMoment, equilibriumResidual);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put(MOMENT_1, moment1);
        values.put(MOMENT_2, moment2);
        values.put(NET_MOMENT, netMoment);
        values.put(EQUILIBRIUM_RESIDUAL, equilibriumResidual);
        return new AnalyticalPoint(values);
    }

    private static double crossProductZ(double positionX, double positionY,
                                        double forceX, double forceY) {
        return positionX * forceY - positionY * forceX;
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Moment-equilibrium quantity " + key
                    + " must use canonical unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Moment-equilibrium quantity must be finite: " + key);
        }
        return value;
    }

    private static void requireFiniteResults(double moment1, double moment2,
                                             double netMoment, double residual) {
        double[] values = {moment1, moment2, netMoment, residual};
        String[] keys = {MOMENT_1, MOMENT_2, NET_MOMENT, EQUILIBRIUM_RESIDUAL};
        for (int index = 0; index < values.length; index++) {
            if (!Double.isFinite(values[index])) {
                throw new ArithmeticException("Moment-equilibrium result is not finite: " + keys[index]);
            }
        }
    }

    /** SI-valued inputs after schema canonicalization and unit normalization. */
    public record Parameters(double force1, double arm1, double angle1,
                             double force2, double arm2, double angle2) {
        public Parameters {
            if (!Double.isFinite(force1) || force1 < 0.0
                    || !Double.isFinite(arm1) || arm1 < 0.0 || !Double.isFinite(angle1)
                    || !Double.isFinite(force2) || force2 < 0.0
                    || !Double.isFinite(arm2) || arm2 < 0.0 || !Double.isFinite(angle2)) {
                throw new IllegalArgumentException("Moment-equilibrium forces and arms must be non-negative and all inputs finite");
            }
        }
    }
}
