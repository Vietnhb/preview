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

/** Typed travelling acoustic pressure field and probe model. */
public final class SoundWaveModule implements PhysicsModule<SoundWaveModule.Parameters> {
    public static final String MODULE_ID = "sound_wave";
    public static final String NUMERICAL_SOLVER_ID = "sound_wave_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "sound_wave_reference_v2";
    private static final String FIELD_ID = "soundPressure";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double pressureAmplitude = requireCanonical(quantities, "pressure_amplitude", "Pa");
        double frequency = requireCanonical(quantities, "frequency", "Hz");
        double soundSpeed = requireCanonical(quantities, "sound_speed", "m/s");
        double phase = requireCanonical(quantities, "phase", "rad");
        double domainStart = requireCanonical(quantities, "domain_start", "m");
        double domainEnd = requireCanonical(quantities, "domain_end", "m");
        double sampleCount = requireCanonical(quantities, "spatial_samples", "1");
        if (!Double.isFinite(sampleCount) || sampleCount != Math.rint(sampleCount)
                || sampleCount < 2.0 || sampleCount > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("Sound-wave spatial_samples must be an integer within scalar-field limits");
        }
        double probePosition = requireCanonical(quantities, "probe_position", "m");
        return new Parameters(pressureAmplitude, frequency, soundSpeed, phase,
                domainStart, domainEnd, (int) sampleCount, probePosition);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        double angularFrequency = 2.0 * Math.PI * parameters.frequency();
        double waveNumber = angularFrequency / parameters.soundSpeed();
        requireFinite(angularFrequency, "angular frequency");
        requireFinite(waveNumber, "wave number");
        validateSampling(parameters, clock);

        int timeSamples = WaveFieldGrid.timeSamples(clock.durationSeconds(), clock.stepSeconds());
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples());
        List<Double> time = WaveFieldGrid.timeCoordinates(clock.durationSeconds(), clock.stepSeconds(), timeSamples);
        List<Double> x = WaveFieldGrid.spaceCoordinates(
                parameters.domainStart(), parameters.domainEnd(), parameters.spatialSamples());
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> pressure = new ArrayList<>(timeSamples);
        List<Double> pressureRate = new ArrayList<>(timeSamples);
        List<Double> pressureAcceleration = new ArrayList<>(timeSamples);

        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(x.size());
            for (double coordinate : x) {
                row.add(evaluate(parameters, coordinate, currentTime, angularFrequency, waveNumber).pressure());
            }
            rows.add(List.copyOf(row));
            State probe = evaluate(parameters, parameters.probePosition(), currentTime, angularFrequency, waveNumber);
            pressure.add(probe.pressure());
            pressureRate.add(probe.rate());
            pressureAcceleration.add(probe.acceleration());
        }

        double spaceStep = (parameters.domainEnd() - parameters.domainStart())
                / (parameters.spatialSamples() - 1.0);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "Pa", "s", new ScalarField.Sampling(spaceStep, clock.stepSeconds()), "linear",
                "open;acoustic_pressure;direction=+x");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("pressure", List.copyOf(pressure));
        values.put("pressureRate", List.copyOf(pressureRate));
        values.put("pressureAcceleration", List.copyOf(pressureAcceleration));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Sound-wave reference time must be finite and non-negative");
        }

        // Use wavelength and cycles at the probe, independently of the numerical
        // path's wave-number/angular-frequency phase construction.
        double wavelength = parameters.soundSpeed() / parameters.frequency();
        double cycles = parameters.frequency()
                * (parameters.probePosition() / parameters.soundSpeed() - timeSeconds);
        double angle = 2.0 * Math.PI * cycles + parameters.phase();
        double pressure = parameters.pressureAmplitude() * Math.cos(angle);
        double pressureRate = parameters.pressureAmplitude() * (2.0 * Math.PI * parameters.frequency())
                * Math.sin(angle);
        double pressureAcceleration = -pressure * (4.0 * Math.PI * Math.PI)
                * parameters.frequency() * parameters.frequency();
        requireFinite(wavelength, "reference wavelength");
        requireFinite(pressure, "reference pressure");
        requireFinite(pressureRate, "reference pressure rate");
        requireFinite(pressureAcceleration, "reference pressure acceleration");
        return new AnalyticalPoint(Map.of("pressure", pressure, "pressureRate", pressureRate,
                "pressureAcceleration", pressureAcceleration));
    }

    private static State evaluate(Parameters parameters, double x, double time,
                                  double angularFrequency, double waveNumber) {
        double angle = waveNumber * x - angularFrequency * time + parameters.phase();
        double pressure = parameters.pressureAmplitude() * Math.cos(angle);
        double rate = parameters.pressureAmplitude() * angularFrequency * Math.sin(angle);
        double acceleration = -angularFrequency * angularFrequency * pressure;
        requireFinite(pressure, "pressure");
        requireFinite(rate, "pressure rate");
        requireFinite(acceleration, "pressure acceleration");
        return new State(pressure, rate, acceleration);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical sound-wave quantity " + key + " must use unit " + unit);
        }
        return quantities.require(key);
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Sound-wave " + label + " must be finite");
    }

    private static void validateSampling(Parameters parameters, SimulationClock clock) {
        double temporalSamplesPerCycle = 1.0 / (parameters.frequency() * clock.stepSeconds());
        double spatialCycles = (parameters.domainEnd() - parameters.domainStart())
                * parameters.frequency() / parameters.soundSpeed();
        double requiredSpatialIntervals = 4.0 * spatialCycles;
        if (!Double.isFinite(temporalSamplesPerCycle) || temporalSamplesPerCycle < 4.0
                || !Double.isFinite(requiredSpatialIntervals)
                || parameters.spatialSamples() - 1.0 < requiredSpatialIntervals) {
            throw new IllegalArgumentException(
                    "Sound-wave time and space grids must provide at least four samples per wave cycle");
        }
    }

    private record State(double pressure, double rate, double acceleration) { }

    /** Immutable SI-valued field inputs after ingress canonicalization. */
    public record Parameters(double pressureAmplitude, double frequency, double soundSpeed,
                             double phase, double domainStart, double domainEnd,
                             int spatialSamples, double probePosition) {
        public Parameters {
            if (!Double.isFinite(pressureAmplitude) || pressureAmplitude < 0.0
                    || !Double.isFinite(frequency) || frequency <= 0.0
                    || !Double.isFinite(soundSpeed) || soundSpeed <= 0.0
                    || !Double.isFinite(phase)
                    || !Double.isFinite(domainStart)
                    || !Double.isFinite(domainEnd) || domainEnd <= domainStart
                    || spatialSamples < 2 || spatialSamples > ScalarField.MAX_SPATIAL_SAMPLES
                    || !Double.isFinite(probePosition)
                    || probePosition < domainStart || probePosition > domainEnd) {
                throw new IllegalArgumentException("Sound-wave inputs must be finite and in their physical domain");
            }
        }
    }
}
