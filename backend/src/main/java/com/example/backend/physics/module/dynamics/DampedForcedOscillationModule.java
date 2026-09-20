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

/** Typed numerical and independent reference implementation for the forced oscillator model. */
public final class DampedForcedOscillationModule
        implements PhysicsModule<DampedForcedOscillationParameters> {
    public static final String MODULE_ID = "damped_forced_oscillation";
    public static final String NUMERICAL_SOLVER_ID = "advanced_oscillation_solver";
    public static final String REFERENCE_SOLVER_ID = "advanced_oscillation_reference";

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
        List<Double> times = clock.sampleTimes();
        List<Double> displacement = new ArrayList<>(times.size());
        List<Double> velocity = new ArrayList<>(times.size());
        List<Double> acceleration = new ArrayList<>(times.size());
        List<Double> energy = new ArrayList<>(times.size());
        List<Double> force = new ArrayList<>(times.size());
        for (double time : times) {
            DampedForcedOscillationParameters.State state = parameters.stateAt(time);
            displacement.add(state.displacement());
            velocity.add(state.velocity());
            acceleration.add(state.acceleration());
            energy.add(0.5 * parameters.mass() * state.velocity() * state.velocity()
                    + 0.5 * parameters.springConstant() * state.displacement() * state.displacement());
            force.add(parameters.drivingAmplitude() * Math.cos(parameters.drivingFrequency() * time));
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
}
