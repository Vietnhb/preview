package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.dynamics.DampedForcedOscillationParameters;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed numerical and independent reference implementation for the forced oscillator model. */
public final class DampedForcedOscillationModule
        implements PhysicsModule<DampedForcedOscillationParameters> {
    public static final String MODULE_ID = "damped_forced_oscillation";
    public static final String NUMERICAL_SOLVER_ID = "advanced_oscillation_solver";
    public static final String REFERENCE_SOLVER_ID = "advanced_oscillation_reference";
    private static final double MAX_NUMERICAL_PHASE_STEP = 1.0e-3;
    private static final int MAX_NUMERICAL_SUBSTEPS_PER_INTERVAL = 100_000;

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
    public DampedForcedOscillationParameters bind(CanonicalQuantityBag quantities) {
        return DampedForcedOscillationParameters.from(quantities);
    }

    @Override
    public SolverOutput solve(DampedForcedOscillationParameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        List<Double> times = clock.sampleTimes();
        List<Double> displacement = new ArrayList<>(times.size());
        List<Double> velocity = new ArrayList<>(times.size());
        List<Double> acceleration = new ArrayList<>(times.size());
        List<Double> energy = new ArrayList<>(times.size());
        List<Double> force = new ArrayList<>(times.size());
        double x = parameters.initialDisplacement();
        double v = parameters.initialVelocity();
        for (double time : times) {
            double a = acceleration(parameters, time, x, v);
            requireFinite(x, v, a);
            displacement.add(x);
            velocity.add(v);
            acceleration.add(a);
            energy.add(0.5 * parameters.mass() * v * v
                    + 0.5 * parameters.springConstant() * x * x);
            force.add(parameters.drivingAmplitude() * Math.cos(parameters.drivingFrequency() * time));
            int index = displacement.size() - 1;
            if (index + 1 < times.size()) {
                double interval = times.get(index + 1) - time;
                NumericalState next = advanceNumerically(parameters, time, x, v, interval);
                x = next.displacement();
                v = next.velocity();
            }
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", List.copyOf(displacement));
        values.put("velocity", List.copyOf(velocity));
        values.put("acceleration", List.copyOf(acceleration));
        values.put("mechanicalEnergy", List.copyOf(energy));
        values.put("drivingForce", List.copyOf(force));
        return new SolverOutput(times, Map.of("x", displacement), Map.of("x", velocity),
                Map.of("x", acceleration), values);
    }

    /**
     * Advances the oscillator with RK4.  This path deliberately integrates the
     * second-order equation directly instead of calling the closed-form
     * stateAt/reference implementation.
     */
    private static NumericalState advanceNumerically(DampedForcedOscillationParameters p,
                                                      double startTime, double x, double v,
                                                      double interval) {
        if (!Double.isFinite(interval) || interval <= 0.0) {
            throw new IllegalArgumentException("Oscillator sample times must increase strictly");
        }
        double rate = Math.max(p.naturalAngularFrequency(),
                Math.max(p.dampingRate(), p.drivingFrequency()));
        double phase = rate * interval;
        if (!Double.isFinite(phase)) {
            throw new ArithmeticException("Oscillator numerical interval is outside the finite domain");
        }
        int substeps = Math.max(1, (int) Math.ceil(phase / MAX_NUMERICAL_PHASE_STEP));
        if (substeps > MAX_NUMERICAL_SUBSTEPS_PER_INTERVAL) {
            throw new ArithmeticException("Oscillator interval requires too many numerical substeps");
        }
        double dt = interval / substeps;
        double time = startTime;
        for (int step = 0; step < substeps; step++) {
            double k1x = v;
            double k1v = acceleration(p, time, x, v);
            double k2x = v + 0.5 * dt * k1v;
            double k2v = acceleration(p, time + 0.5 * dt,
                    x + 0.5 * dt * k1x, v + 0.5 * dt * k1v);
            double k3x = v + 0.5 * dt * k2v;
            double k3v = acceleration(p, time + 0.5 * dt,
                    x + 0.5 * dt * k2x, v + 0.5 * dt * k2v);
            double k4x = v + dt * k3v;
            double k4v = acceleration(p, time + dt, x + dt * k3x, v + dt * k3v);
            x += dt * (k1x + 2.0 * k2x + 2.0 * k3x + k4x) / 6.0;
            v += dt * (k1v + 2.0 * k2v + 2.0 * k3v + k4v) / 6.0;
            time += dt;
            requireFinite(x, v);
        }
        return new NumericalState(x, v);
    }

    private static double acceleration(DampedForcedOscillationParameters p,
                                       double time, double displacement, double velocity) {
        double value = (p.drivingAmplitude() * Math.cos(p.drivingFrequency() * time)
                - p.dampingCoefficient() * velocity
                - p.springConstant() * displacement) / p.mass();
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Oscillator numerical acceleration is not finite");
        }
        return value;
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new ArithmeticException("Oscillator numerical output is not finite");
            }
        }
    }

    @Override
    public AnalyticalPoint referenceAt(DampedForcedOscillationParameters parameters, double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }

        // Rebuild the closed-form solution from primitive inputs. Keep this oracle
        // separate from parameter-derived methods and stateAt so it can check them.
        double mass = parameters.mass();
        double stiffness = parameters.springConstant();
        double damping = parameters.dampingCoefficient();
        double forceAmplitude = parameters.drivingAmplitude();
        double driveFrequency = parameters.drivingFrequency();
        double naturalFrequency = Math.sqrt(stiffness / mass);
        double decayRate = damping / (2.0 * mass);

        double frequencyGap = naturalFrequency * naturalFrequency - driveFrequency * driveFrequency;
        double quadrature = 2.0 * decayRate * driveFrequency;
        double responseDenominator = Math.sqrt(frequencyGap * frequencyGap + quadrature * quadrature);
        if (responseDenominator == 0.0 && forceAmplitude != 0.0) {
            throw new IllegalArgumentException("Undamped resonant steady-state amplitude is unbounded");
        }
        double responseAmplitude = responseDenominator == 0.0
                ? 0.0 : (forceAmplitude / mass) / responseDenominator;
        double responsePhase = Math.atan2(quadrature, frequencyGap);

        double phase = driveFrequency * timeSeconds - responsePhase;
        double particularX = responseAmplitude * Math.cos(phase);
        double particularV = -responseAmplitude * driveFrequency * Math.sin(phase);
        double particularA = -responseAmplitude * driveFrequency * driveFrequency * Math.cos(phase);

        double initialHomogeneousX = parameters.initialDisplacement()
                - responseAmplitude * Math.cos(responsePhase);
        double initialHomogeneousV = parameters.initialVelocity()
                - responseAmplitude * driveFrequency * Math.sin(responsePhase);
        double homogeneousX;
        double homogeneousV;
        double homogeneousA;
        // Use a dimensionless relative gap so low-frequency systems are not
        // classified as critical merely because both rates are below 1 rad/s.
        double rootScale = Math.max(Math.abs(decayRate), Math.abs(naturalFrequency));
        double relativeRootGap = rootScale == 0.0 ? Double.POSITIVE_INFINITY
                : Math.abs(decayRate - naturalFrequency) / rootScale;

        if (relativeRootGap <= 1.0e-12) {
            double envelope = Math.exp(-decayRate * timeSeconds);
            double linearCoefficient = initialHomogeneousV + decayRate * initialHomogeneousX;
            double polynomial = initialHomogeneousX + linearCoefficient * timeSeconds;
            homogeneousX = envelope * polynomial;
            homogeneousV = envelope * (linearCoefficient - decayRate * polynomial);
            homogeneousA = -2.0 * decayRate * homogeneousV
                    - naturalFrequency * naturalFrequency * homogeneousX;
        } else if (decayRate < naturalFrequency) {
            double dampedFrequency = Math.sqrt(naturalFrequency * naturalFrequency - decayRate * decayRate);
            double sineCoefficient = (initialHomogeneousV + decayRate * initialHomogeneousX) / dampedFrequency;
            double angle = dampedFrequency * timeSeconds;
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            double envelope = Math.exp(-decayRate * timeSeconds);
            homogeneousX = envelope * (initialHomogeneousX * cosine + sineCoefficient * sine);
            homogeneousV = envelope * ((sineCoefficient * dampedFrequency
                    - decayRate * initialHomogeneousX) * cosine
                    + (-initialHomogeneousX * dampedFrequency - decayRate * sineCoefficient) * sine);
            homogeneousA = -2.0 * decayRate * homogeneousV
                    - naturalFrequency * naturalFrequency * homogeneousX;
        } else {
            double root = Math.sqrt(decayRate * decayRate - naturalFrequency * naturalFrequency);
            double slowRoot = -decayRate + root;
            double fastRoot = -decayRate - root;
            double slowCoefficient = (initialHomogeneousV - fastRoot * initialHomogeneousX)
                    / (slowRoot - fastRoot);
            double fastCoefficient = initialHomogeneousX - slowCoefficient;
            double slowTerm = slowCoefficient * Math.exp(slowRoot * timeSeconds);
            double fastTerm = fastCoefficient * Math.exp(fastRoot * timeSeconds);
            homogeneousX = slowTerm + fastTerm;
            homogeneousV = slowRoot * slowTerm + fastRoot * fastTerm;
            homogeneousA = -2.0 * decayRate * homogeneousV
                    - naturalFrequency * naturalFrequency * homogeneousX;
        }

        double x = particularX + homogeneousX;
        double v = particularV + homogeneousV;
        double a = particularA + homogeneousA;
        double mechanicalEnergy = 0.5 * mass * v * v + 0.5 * stiffness * x * x;
        double drivingForce = forceAmplitude * Math.cos(driveFrequency * timeSeconds);
        return new AnalyticalPoint(Map.of(
                "displacement", x,
                "velocity", v,
                "acceleration", a,
                "mechanicalEnergy", mechanicalEnergy,
                "drivingForce", drivingForce));
    }

    private record NumericalState(double displacement, double velocity) { }
}
