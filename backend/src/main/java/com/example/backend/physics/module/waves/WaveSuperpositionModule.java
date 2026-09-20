package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.WaveFieldGrid;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed two-component travelling-wave solver with a phasor-form reference oracle. */
public final class WaveSuperpositionModule implements PhysicsModule<WaveSuperpositionModule.Parameters> {
    public static final String MODULE_ID = "wave_superposition";
    public static final String NUMERICAL_SOLVER_ID = "wave_superposition_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "wave_superposition_reference_v2";
    private static final String FIELD_ID = "superpositionDisplacement";
    private static final double MINIMUM_SAMPLES_PER_CYCLE = 4.0;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "amplitude_1", "m"),
                requireCanonical(quantities, "amplitude_2", "m"),
                requireCanonical(quantities, "frequency", "Hz"),
                requireCanonical(quantities, "wave_speed", "m/s"),
                requireCanonical(quantities, "phase_1", "rad"),
                requireCanonical(quantities, "phase_2", "rad"),
                requireCanonical(quantities, "domain_start", "m"),
                requireCanonical(quantities, "domain_end", "m"),
                requireSampleCount(quantities),
                requireCanonical(quantities, "probe_position", "m"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        double angularFrequency = 2.0 * Math.PI * parameters.frequency();
        double waveNumber = angularFrequency / parameters.waveSpeed();
        requireFinite(angularFrequency, "angular frequency");
        requireFinite(waveNumber, "wave number");
        validateSampling(parameters, clock);

        int timeSamples = WaveFieldGrid.timeSamples(clock.durationSeconds(), clock.stepSeconds());
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples());
        List<Double> time = WaveFieldGrid.timeCoordinates(
                clock.durationSeconds(), clock.stepSeconds(), timeSamples);
        List<Double> x = WaveFieldGrid.spaceCoordinates(
                parameters.domainStart(), parameters.domainEnd(), parameters.spatialSamples());
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> displacement = new ArrayList<>(timeSamples);
        List<Double> velocity = new ArrayList<>(timeSamples);
        List<Double> acceleration = new ArrayList<>(timeSamples);

        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(x.size());
            for (double coordinate : x) {
                row.add(numericalState(parameters, coordinate, currentTime,
                        angularFrequency, waveNumber).displacement());
            }
            rows.add(List.copyOf(row));
            State probe = numericalState(parameters, parameters.probePosition(), currentTime,
                    angularFrequency, waveNumber);
            displacement.add(probe.displacement());
            velocity.add(probe.velocity());
            acceleration.add(probe.acceleration());
        }

        double spaceStep = (parameters.domainEnd() - parameters.domainStart())
                / (parameters.spatialSamples() - 1.0);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "m", "s", new ScalarField.Sampling(spaceStep, clock.stepSeconds()), "linear",
                "open;superposition;components=2;direction=+x");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", List.copyOf(displacement));
        values.put("particleVelocity", List.copyOf(velocity));
        values.put("particleAcceleration", List.copyOf(acceleration));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values,
                Map.of(FIELD_ID, field));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Wave-superposition reference time must be finite and non-negative");
        }

        // The oracle first combines the two source phasors, then advances the
        // resultant carrier by cycles. It does not evaluate the numerical
        // solver's component-wise cosine and sine sums.
        double wavelength = parameters.waveSpeed() / parameters.frequency();
        requirePositiveFinite(wavelength, "reference wavelength");
        double spatialCycles = parameters.probePosition() / wavelength;
        double temporalCycles = parameters.frequency() * timeSeconds;
        double carrierPhase = 2.0 * Math.PI * (spatialCycles - temporalCycles);
        double inPhase = parameters.amplitude1() * Math.cos(parameters.phase1())
                + parameters.amplitude2() * Math.cos(parameters.phase2());
        double quadrature = parameters.amplitude1() * Math.sin(parameters.phase1())
                + parameters.amplitude2() * Math.sin(parameters.phase2());
        double displacement = inPhase * Math.cos(carrierPhase) - quadrature * Math.sin(carrierPhase);
        double angularFrequency = 2.0 * Math.PI * parameters.frequency();
        double velocity = angularFrequency
                * (inPhase * Math.sin(carrierPhase) + quadrature * Math.cos(carrierPhase));
        double acceleration = -(angularFrequency * angularFrequency) * displacement;
        requireFinite(spatialCycles, "reference spatial cycles");
        requireFinite(temporalCycles, "reference temporal cycles");
        requireFinite(carrierPhase, "reference carrier phase");
        requireFinite(displacement, "reference displacement");
        requireFinite(velocity, "reference particle velocity");
        requireFinite(acceleration, "reference particle acceleration");
        return new AnalyticalPoint(Map.of("displacement", displacement,
                "particleVelocity", velocity, "particleAcceleration", acceleration));
    }

    private static State numericalState(Parameters parameters, double x, double time,
                                        double angularFrequency, double waveNumber) {
        double carrier = waveNumber * x - angularFrequency * time;
        double phase1 = carrier + parameters.phase1();
        double phase2 = carrier + parameters.phase2();
        requireFinite(phase1, "component 1 phase");
        requireFinite(phase2, "component 2 phase");
        double displacement = parameters.amplitude1() * Math.cos(phase1)
                + parameters.amplitude2() * Math.cos(phase2);
        double velocity = angularFrequency * (parameters.amplitude1() * Math.sin(phase1)
                + parameters.amplitude2() * Math.sin(phase2));
        double acceleration = -(angularFrequency * angularFrequency) * displacement;
        requireFinite(displacement, "displacement");
        requireFinite(velocity, "particle velocity");
        requireFinite(acceleration, "particle acceleration");
        return new State(displacement, velocity, acceleration);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical wave-superposition quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canonical wave-superposition quantity must be finite: " + key);
        }
        return value;
    }

    private static int requireSampleCount(CanonicalQuantityBag quantities) {
        double samples = requireCanonical(quantities, "spatial_samples", "1");
        if (samples != Math.rint(samples) || samples < 2.0
                || samples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException(
                    "Wave-superposition spatial_samples must be an integer within scalar-field limits");
        }
        return (int) samples;
    }

    private static void validateSampling(Parameters parameters, SimulationClock clock) {
        double wavelength = parameters.waveSpeed() / parameters.frequency();
        requirePositiveFinite(wavelength, "wavelength");
        double spaceStep = (parameters.domainEnd() - parameters.domainStart())
                / (parameters.spatialSamples() - 1.0);
        double temporalSamplesPerCycle = 1.0 / (parameters.frequency() * clock.stepSeconds());
        if (!Double.isFinite(spaceStep) || spaceStep <= 0.0
                || !Double.isFinite(temporalSamplesPerCycle)
                || spaceStep > wavelength / MINIMUM_SAMPLES_PER_CYCLE
                || temporalSamplesPerCycle < MINIMUM_SAMPLES_PER_CYCLE) {
            throw new IllegalArgumentException(
                    "Wave-superposition grids must provide at least four samples per cycle in space and time");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Wave-superposition " + label + " must be finite");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        requireFinite(value, label);
        if (value <= 0.0) throw new ArithmeticException("Wave-superposition " + label + " must be positive");
    }

    private record State(double displacement, double velocity, double acceleration) { }

    /** SI-valued inputs after schema defaults and unit normalization have been applied. */
    public record Parameters(double amplitude1, double amplitude2, double frequency, double waveSpeed,
                             double phase1, double phase2, double domainStart, double domainEnd,
                             int spatialSamples, double probePosition) {
        public Parameters {
            double domainLength = domainEnd - domainStart;
            if (!Double.isFinite(amplitude1) || amplitude1 < 0.0
                    || !Double.isFinite(amplitude2) || amplitude2 < 0.0
                    || !Double.isFinite(frequency) || frequency <= 0.0
                    || !Double.isFinite(waveSpeed) || waveSpeed <= 0.0
                    || !Double.isFinite(phase1) || !Double.isFinite(phase2)
                    || !Double.isFinite(domainStart) || !Double.isFinite(domainEnd)
                    || !Double.isFinite(domainLength) || domainLength <= 0.0
                    || spatialSamples < 2 || spatialSamples > ScalarField.MAX_SPATIAL_SAMPLES
                    || !Double.isFinite(probePosition)
                    || probePosition < domainStart || probePosition > domainEnd) {
                throw new IllegalArgumentException(
                        "Wave-superposition inputs must be finite and within their physical domain");
            }
        }
    }
}
