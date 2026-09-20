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

/** Typed standing-wave field and independently expressed probe oracle. */
public final class StandingWaveModule implements PhysicsModule<StandingWaveModule.Parameters> {
    public static final String MODULE_ID = "standing_wave";
    public static final String NUMERICAL_SOLVER_ID = "standing_wave_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "standing_wave_reference_v2";
    private static final String FIELD_ID = "standingDisplacement";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double amplitude = requireCanonical(quantities, "amplitude", "m");
        double frequency = requireCanonical(quantities, "frequency", "Hz");
        double waveSpeed = requireCanonical(quantities, "wave_speed", "m/s");
        double stringLength = requireCanonical(quantities, "string_length", "m");
        double phase = requireCanonical(quantities, "phase", "rad");
        double domainStart = requireCanonical(quantities, "domain_start", "m");
        double sampleCount = requireCanonical(quantities, "spatial_samples", "1");
        if (sampleCount != Math.rint(sampleCount) || sampleCount < 2.0
                || sampleCount > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("Standing-wave spatial_samples must be an integer within scalar-field limits");
        }
        double probePosition = requireCanonical(quantities, "probe_position", "m");
        return new Parameters(amplitude, frequency, waveSpeed, phase, domainStart,
                stringLength, (int) sampleCount, probePosition);
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
                row.add(evaluate(parameters, coordinate, currentTime, angularFrequency, waveNumber).displacement());
            }
            rows.add(List.copyOf(row));
            State probe = evaluate(parameters, parameters.probePosition(), currentTime,
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
                "open;standing;counter_propagating");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", List.copyOf(displacement));
        values.put("particleVelocity", List.copyOf(velocity));
        values.put("particleAcceleration", List.copyOf(acceleration));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Standing-wave reference time must be finite and non-negative");
        }

        // The oracle works in cycles per wavelength instead of reusing the
        // numerical path's wave-number and angular-frequency construction.
        double wavelength = parameters.waveSpeed() / parameters.frequency();
        requirePositiveFinite(wavelength, "reference wavelength");
        double spatialCycles = (parameters.probePosition() - parameters.domainStart()) / wavelength;
        double temporalCycles = parameters.frequency() * timeSeconds;
        double spatialShape = Math.sin(2.0 * Math.PI * spatialCycles);
        double temporalPhase = 2.0 * Math.PI * temporalCycles + parameters.phase();
        double displacement = parameters.amplitude() * spatialShape * Math.cos(temporalPhase);
        double angularFrequency = 2.0 * Math.PI * parameters.frequency();
        double velocity = -parameters.amplitude() * spatialShape * angularFrequency * Math.sin(temporalPhase);
        double acceleration = -(angularFrequency * angularFrequency) * displacement;
        requireFinite(spatialCycles, "reference spatial cycles");
        requireFinite(temporalCycles, "reference temporal cycles");
        requireFinite(displacement, "reference displacement");
        requireFinite(velocity, "reference particle velocity");
        requireFinite(acceleration, "reference particle acceleration");
        return new AnalyticalPoint(Map.of("displacement", displacement,
                "particleVelocity", velocity, "particleAcceleration", acceleration));
    }

    private static State evaluate(Parameters parameters, double x, double time,
                                  double angularFrequency, double waveNumber) {
        double spatialShape = Math.sin(waveNumber * (x - parameters.domainStart()));
        double temporalPhase = angularFrequency * time + parameters.phase();
        double displacement = parameters.amplitude() * spatialShape * Math.cos(temporalPhase);
        double velocity = -parameters.amplitude() * spatialShape * angularFrequency * Math.sin(temporalPhase);
        double acceleration = -(angularFrequency * angularFrequency) * displacement;
        requireFinite(displacement, "displacement");
        requireFinite(velocity, "particle velocity");
        requireFinite(acceleration, "particle acceleration");
        return new State(displacement, velocity, acceleration);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical standing-wave quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canonical standing-wave quantity must be finite: " + key);
        }
        return value;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Standing-wave " + label + " must be finite");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        requireFinite(value, label);
        if (value <= 0.0) throw new ArithmeticException("Standing-wave " + label + " must be positive");
    }

    private static void validateSampling(Parameters parameters, SimulationClock clock) {
        double temporalSamplesPerCycle = 1.0 / (parameters.frequency() * clock.stepSeconds());
        double spatialCycles = parameters.stringLength() * parameters.frequency() / parameters.waveSpeed();
        double requiredSpatialIntervals = 4.0 * spatialCycles;
        if (!Double.isFinite(temporalSamplesPerCycle) || temporalSamplesPerCycle < 4.0
                || !Double.isFinite(requiredSpatialIntervals)
                || parameters.spatialSamples() - 1.0 < requiredSpatialIntervals) {
            throw new IllegalArgumentException(
                    "Standing-wave time and space grids must provide at least four samples per wave cycle");
        }
    }

    private record State(double displacement, double velocity, double acceleration) { }

    /** SI-valued field and probe inputs after ingress canonicalization. */
    public record Parameters(double amplitude, double frequency, double waveSpeed, double phase,
                             double domainStart, double stringLength, int spatialSamples,
                             double probePosition) {
        public Parameters {
            double domainEnd = domainStart + stringLength;
            if (!Double.isFinite(amplitude) || amplitude < 0.0
                    || !Double.isFinite(frequency) || frequency <= 0.0
                    || !Double.isFinite(waveSpeed) || waveSpeed <= 0.0
                    || !Double.isFinite(phase)
                    || !Double.isFinite(domainStart)
                    || !Double.isFinite(stringLength) || stringLength <= 0.0
                    || !Double.isFinite(domainEnd) || domainEnd <= domainStart
                    || spatialSamples < 2 || spatialSamples > ScalarField.MAX_SPATIAL_SAMPLES
                    || !Double.isFinite(probePosition)
                    || probePosition < domainStart || probePosition > domainEnd) {
                throw new IllegalArgumentException("Standing-wave inputs must be finite and within their physical domain");
            }
        }

        public double domainEnd() { return domainStart + stringLength; }
    }
}
