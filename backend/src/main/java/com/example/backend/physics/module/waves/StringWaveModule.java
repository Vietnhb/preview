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

/** Typed periodic transverse wave on a string, with a cycle-based reference oracle. */
public final class StringWaveModule implements PhysicsModule<StringWaveModule.Parameters> {
    public static final String MODULE_ID = "string_wave";
    public static final String NUMERICAL_SOLVER_ID = "string_wave_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "string_wave_reference_v2";
    private static final String FIELD_ID = "transverseDisplacement";
    private static final double MINIMUM_SAMPLES_PER_CYCLE = 4.0;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double rawSamples = requireCanonical(quantities, "spatial_samples", "1");
        if (rawSamples != Math.rint(rawSamples) || rawSamples < 2.0
                || rawSamples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException(
                    "String-wave spatial_samples must be an integer within scalar-field limits");
        }
        return new Parameters(
                requireCanonical(quantities, "amplitude", "m"),
                requireCanonical(quantities, "frequency", "Hz"),
                requireCanonical(quantities, "wave_speed", "m/s"),
                requireCanonical(quantities, "phase", "rad"),
                requireCanonical(quantities, "domain_length", "m"),
                (int) rawSamples,
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
        List<Double> time = WaveFieldGrid.timeCoordinates(clock.durationSeconds(), clock.stepSeconds(), timeSamples);
        List<Double> x = WaveFieldGrid.spaceCoordinates(0.0, parameters.length(), parameters.spatialSamples());
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> displacement = new ArrayList<>(timeSamples);
        List<Double> particleVelocity = new ArrayList<>(timeSamples);
        List<Double> particleAcceleration = new ArrayList<>(timeSamples);
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
            particleVelocity.add(probe.velocity());
            particleAcceleration.add(probe.acceleration());
        }

        double spaceStep = parameters.length() / (parameters.spatialSamples() - 1.0);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.SCALAR_FIELD_TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "m", "s", new ScalarField.Sampling(spaceStep, clock.stepSeconds()), "linear",
                "open;harmonic;direction=+x;origin=0m");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", List.copyOf(displacement));
        values.put("particleVelocity", List.copyOf(particleVelocity));
        values.put("particleAcceleration", List.copyOf(particleAcceleration));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("String-wave reference time must be finite and non-negative");
        }

        // Evaluate a reduced number of spatial-temporal cycles. This expression
        // is deliberately separate from the numerical sampler's k*x - omega*t.
        double wavelength = parameters.waveSpeed() / parameters.frequency();
        requirePositiveFinite(wavelength, "reference wavelength");
        double cycles = parameters.probePosition() / wavelength
                - parameters.frequency() * timeSeconds;
        requireFinite(cycles, "reference cycle count");
        double reducedCycles = Math.IEEEremainder(cycles, 1.0);
        double angle = 2.0 * Math.PI * reducedCycles + parameters.phase();
        double angularFrequency = 2.0 * Math.PI * parameters.frequency();
        double displacement = parameters.amplitude() * Math.cos(angle);
        double velocity = parameters.amplitude() * angularFrequency * Math.sin(angle);
        double acceleration = -angularFrequency * angularFrequency * displacement;
        requireFinite(displacement, "reference displacement");
        requireFinite(velocity, "reference particle velocity");
        requireFinite(acceleration, "reference particle acceleration");
        return new AnalyticalPoint(Map.of("displacement", displacement,
                "particleVelocity", velocity, "particleAcceleration", acceleration));
    }

    private static State numericalState(Parameters parameters, double x, double time,
                                        double angularFrequency, double waveNumber) {
        double phase = waveNumber * x - angularFrequency * time + parameters.phase();
        requireFinite(phase, "numerical phase");
        double displacement = parameters.amplitude() * Math.cos(phase);
        double velocity = parameters.amplitude() * angularFrequency * Math.sin(phase);
        double acceleration = -angularFrequency * angularFrequency * displacement;
        requireFinite(displacement, "displacement");
        requireFinite(velocity, "particle velocity");
        requireFinite(acceleration, "particle acceleration");
        return new State(displacement, velocity, acceleration);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical string-wave quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canonical string-wave quantity must be finite: " + key);
        }
        return value;
    }

    private static void validateSampling(Parameters parameters, SimulationClock clock) {
        double wavelength = parameters.waveSpeed() / parameters.frequency();
        requirePositiveFinite(wavelength, "wavelength");
        double spaceStep = parameters.length() / (parameters.spatialSamples() - 1.0);
        double temporalSamplesPerCycle = 1.0 / (parameters.frequency() * clock.stepSeconds());
        if (!Double.isFinite(spaceStep) || spaceStep <= 0.0
                || !Double.isFinite(temporalSamplesPerCycle)
                || spaceStep > wavelength / MINIMUM_SAMPLES_PER_CYCLE
                || temporalSamplesPerCycle < MINIMUM_SAMPLES_PER_CYCLE) {
            throw new IllegalArgumentException(
                    "String-wave grids must provide at least four samples per wavelength and period");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("String-wave " + label + " must be finite");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        requireFinite(value, label);
        if (value <= 0.0) throw new ArithmeticException("String-wave " + label + " must be positive");
    }

    private record State(double displacement, double velocity, double acceleration) { }

    /** SI-valued contract after schema defaults and unit conversion are applied. */
    public record Parameters(double amplitude, double frequency, double waveSpeed, double phase,
                             double length, int spatialSamples, double probePosition) {
        public Parameters {
            double wavelength = waveSpeed / frequency;
            if (!Double.isFinite(amplitude) || amplitude < 0.0 || amplitude > 1.0
                    || !Double.isFinite(frequency) || frequency < 0.1 || frequency > 5.0
                    || !Double.isFinite(waveSpeed) || waveSpeed < 1.0 || waveSpeed > 100.0
                    || !Double.isFinite(phase) || phase < -2.0 * Math.PI || phase > 2.0 * Math.PI
                    || !Double.isFinite(length) || length < 0.1 || length > 20.0
                    || !Double.isFinite(wavelength) || wavelength <= 0.0
                    || spatialSamples < 2 || spatialSamples > ScalarField.MAX_SPATIAL_SAMPLES
                    || !Double.isFinite(probePosition) || probePosition < 0.0 || probePosition > length) {
                throw new IllegalArgumentException(
                        "String-wave inputs must be finite and within their physical domain");
            }
        }
    }
}
