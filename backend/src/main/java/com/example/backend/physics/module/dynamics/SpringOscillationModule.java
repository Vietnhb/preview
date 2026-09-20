package com.example.backend.physics.module.dynamics;

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

/** Typed ideal spring oscillator; numerical output uses bounded velocity-Verlet steps. */
public final class SpringOscillationModule implements PhysicsModule<SpringOscillationModule.Parameters> {
    public static final String MODULE_ID = "oscillations_spring";
    public static final String NUMERICAL_SOLVER_ID = "spring_oscillation_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "spring_oscillation_reference_v2";
    private static final double MAX_PHASE_STEP = 0.1;
    private static final int MAX_SUBSTEPS_PER_SAMPLE = 100_000;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "amplitude", "m");
        requireUnit(quantities, "mass", "kg");
        requireUnit(quantities, "spring_constant", "N/m");
        requireUnit(quantities, "phase", "rad");
        return new Parameters(quantities.require("amplitude"), quantities.require("mass"),
                quantities.require("spring_constant"), quantities.require("phase"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double omega = Math.sqrt(p.springConstant() / p.mass());
        double omegaSquared = omega * omega;
        requireFinite(omega, omegaSquared);
        double x = p.amplitude() * Math.cos(p.phase());
        double v = -p.amplitude() * omega * Math.sin(p.phase());
        List<Double> position = new ArrayList<>(time.size());
        List<Double> velocity = new ArrayList<>(time.size());
        List<Double> acceleration = new ArrayList<>(time.size());
        List<Double> xValue = new ArrayList<>(time.size());
        List<Double> vxValue = new ArrayList<>(time.size());
        List<Double> axValue = new ArrayList<>(time.size());
        for (int i = 0; i < time.size(); i++) {
            double a = -omegaSquared * x;
            requireFinite(x, v, a);
            position.add(x);
            velocity.add(v);
            acceleration.add(a);
            xValue.add(x);
            vxValue.add(v);
            axValue.add(a);
            if (i + 1 < time.size()) {
                double interval = time.get(i + 1) - time.get(i);
                double requestedSteps = Math.ceil(interval * omega / MAX_PHASE_STEP);
                if (!Double.isFinite(requestedSteps) || requestedSteps > MAX_SUBSTEPS_PER_SAMPLE) {
                    throw new ArithmeticException("Spring frequency requires too many numerical substeps");
                }
                int substeps = Math.max(1, (int) requestedSteps);
                double dt = interval / substeps;
                for (int substep = 0; substep < substeps; substep++) {
                    double oldAcceleration = -omegaSquared * x;
                    double nextX = x + v * dt + 0.5 * oldAcceleration * dt * dt;
                    double nextAcceleration = -omegaSquared * nextX;
                    double nextV = v + 0.5 * (oldAcceleration + nextAcceleration) * dt;
                    requireFinite(nextX, nextV, nextAcceleration);
                    x = nextX;
                    v = nextV;
                }
            }
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x", List.copyOf(xValue));
        values.put("vx", List.copyOf(vxValue));
        values.put("ax", List.copyOf(axValue));
        return new SolverOutput(time, Map.of("position", List.copyOf(position)),
                Map.of("velocity", List.copyOf(velocity)), Map.of("acceleration", List.copyOf(acceleration)), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double omega = Math.sqrt(p.springConstant() / p.mass());
        double angle = omega * timeSeconds + p.phase();
        double position = p.amplitude() * Math.cos(angle);
        double velocity = -p.amplitude() * omega * Math.sin(angle);
        double acceleration = -(p.springConstant() / p.mass()) * position;
        requireFinite(position, velocity, acceleration);
        return new AnalyticalPoint(Map.of("x", position, "vx", velocity, "ax", acceleration));
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String unit) {
        if (!unit.equals(values.unit(key))) throw new IllegalArgumentException("Spring " + key + " must use " + unit);
    }
    private static void requireCheckpoint(double t) {
        if (!Double.isFinite(t) || t < 0.0) throw new IllegalArgumentException("Spring reference time is invalid");
    }
    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Spring output is not finite");
    }

    public record Parameters(double amplitude, double mass, double springConstant, double phase) {
        public Parameters {
            if (!Double.isFinite(amplitude) || amplitude <= 0.0 || !Double.isFinite(mass) || mass <= 0.0
                    || !Double.isFinite(springConstant) || springConstant <= 0.0 || !Double.isFinite(phase)) {
                throw new IllegalArgumentException("Spring amplitude, mass, and stiffness must be positive and finite; phase finite");
            }
        }
    }
}
