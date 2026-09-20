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

/** Typed Gaussian travelling pulse and algebraically independent probe oracle. */
public final class WavePulseModule implements PhysicsModule<WavePulseModule.Parameters> {
    public static final String MODULE_ID = "wave_pulse";
    public static final String NUMERICAL_SOLVER_ID = "wave_pulse_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "wave_pulse_reference_v2";
    private static final String FIELD_ID = "pulseDisplacement";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double amplitude = requireCanonical(quantities, "amplitude", "m");
        double waveSpeed = requireCanonical(quantities, "wave_speed", "m/s");
        double width = requireCanonical(quantities, "pulse_width", "m");
        double initialPosition = requireCanonical(quantities, "initial_position", "m");
        double domainStart = requireCanonical(quantities, "domain_start", "m");
        double domainEnd = requireCanonical(quantities, "domain_end", "m");
        double rawSamples = requireCanonical(quantities, "spatial_samples", "1");
        if (rawSamples != Math.rint(rawSamples) || rawSamples < 2.0
                || rawSamples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException(
                    "Wave-pulse spatial_samples must be an integer within scalar-field limits");
        }
        double probePosition = requireCanonical(quantities, "probe_position", "m");
        return new Parameters(amplitude, waveSpeed, width, initialPosition, domainStart,
                domainEnd, (int) rawSamples, probePosition);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        double terminalCenter = parameters.initialPosition() + parameters.waveSpeed() * clock.durationSeconds();
        if (!Double.isFinite(terminalCenter)) {
            throw new ArithmeticException("Wave-pulse center must remain finite over the simulation horizon");
        }
        int timeSamples = WaveFieldGrid.timeSamples(clock.durationSeconds(), clock.stepSeconds());
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples());
        validateSampling(parameters, clock);

        List<Double> time = WaveFieldGrid.timeCoordinates(clock.durationSeconds(), clock.stepSeconds(), timeSamples);
        List<Double> x = WaveFieldGrid.spaceCoordinates(
                parameters.domainStart(), parameters.domainEnd(), parameters.spatialSamples());
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> displacement = new ArrayList<>(timeSamples);
        List<Double> velocity = new ArrayList<>(timeSamples);
        List<Double> acceleration = new ArrayList<>(timeSamples);
        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(x.size());
            for (double coordinate : x) {
                row.add(numericalState(parameters, coordinate, currentTime).displacement());
            }
            rows.add(List.copyOf(row));
            State probe = numericalState(parameters, parameters.probePosition(), currentTime);
            displacement.add(probe.displacement());
            velocity.add(probe.velocity());
            acceleration.add(probe.acceleration());
        }

        double spaceStep = (parameters.domainEnd() - parameters.domainStart())
                / (parameters.spatialSamples() - 1.0);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "m", "s", new ScalarField.Sampling(spaceStep, clock.stepSeconds()), "linear",
                "open;travelling_pulse;direction=+x");
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
            throw new IllegalArgumentException("Wave-pulse reference time must be finite and non-negative");
        }

        // The oracle uses the dimensional probe-to-center offset and powers of
        // width; it does not call or reproduce the numerical path's q-derivative.
        double center = parameters.initialPosition() + parameters.waveSpeed() * timeSeconds;
        double offset = parameters.probePosition() - center;
        double widthSquared = parameters.width() * parameters.width();
        double scaledOffset = offset / parameters.width();
        double displacement = parameters.amplitude() * Math.exp(-(scaledOffset * scaledOffset));
        double velocity = (2.0 * parameters.waveSpeed() * offset / widthSquared) * displacement;
        double acceleration = ((4.0 * offset * offset / (widthSquared * widthSquared))
                - (2.0 / widthSquared)) * parameters.waveSpeed() * parameters.waveSpeed() * displacement;
        requireFinite(center, "reference center");
        requireFinite(offset, "reference offset");
        requireFinite(displacement, "reference displacement");
        requireFinite(velocity, "reference particle velocity");
        requireFinite(acceleration, "reference particle acceleration");
        return new AnalyticalPoint(Map.of("displacement", displacement,
                "particleVelocity", velocity, "particleAcceleration", acceleration));
    }

    private static State numericalState(Parameters parameters, double x, double time) {
        double q = (x - parameters.initialPosition() - parameters.waveSpeed() * time) / parameters.width();
        double envelope = Math.exp(-q * q);
        double speedOverWidth = parameters.waveSpeed() / parameters.width();
        double displacement = parameters.amplitude() * envelope;
        double velocity = parameters.amplitude() * envelope * 2.0 * q * speedOverWidth;
        double acceleration = parameters.amplitude() * envelope * (4.0 * q * q - 2.0)
                * speedOverWidth * speedOverWidth;
        requireFinite(q, "scaled pulse coordinate");
        requireFinite(displacement, "displacement");
        requireFinite(velocity, "particle velocity");
        requireFinite(acceleration, "particle acceleration");
        return new State(displacement, velocity, acceleration);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical wave-pulse quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canonical wave-pulse quantity must be finite: " + key);
        }
        return value;
    }

    private static void validateSampling(Parameters parameters, SimulationClock clock) {
        double dx = (parameters.domainEnd() - parameters.domainStart())
                / (parameters.spatialSamples() - 1.0);
        double spatialSamplesPerWidth = parameters.width() / dx;
        double temporalSamplesPerWidth = parameters.width() / parameters.waveSpeed() / clock.stepSeconds();
        if (!Double.isFinite(dx) || dx <= 0.0 || !Double.isFinite(spatialSamplesPerWidth)
                || spatialSamplesPerWidth < 4.0 || !Double.isFinite(temporalSamplesPerWidth)
                || temporalSamplesPerWidth < 4.0) {
            throw new IllegalArgumentException(
                    "Wave-pulse grids must provide at least four samples per pulse width in space and time");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Wave-pulse " + label + " must be finite");
    }

    private record State(double displacement, double velocity, double acceleration) { }

    /** SI-valued quantities after schema defaults and units have been resolved at ingress. */
    public record Parameters(double amplitude, double waveSpeed, double width, double initialPosition,
                             double domainStart, double domainEnd, int spatialSamples, double probePosition) {
        public Parameters {
            if (!Double.isFinite(amplitude) || amplitude < 0.0
                    || !Double.isFinite(waveSpeed) || waveSpeed <= 0.0
                    || !Double.isFinite(width) || width <= 0.0
                    || !Double.isFinite(initialPosition)
                    || !Double.isFinite(domainStart) || !Double.isFinite(domainEnd)
                    || domainEnd <= domainStart
                    || initialPosition < domainStart || initialPosition > domainEnd
                    || spatialSamples < 2 || spatialSamples > ScalarField.MAX_SPATIAL_SAMPLES
                    || !Double.isFinite(probePosition)
                    || probePosition < domainStart || probePosition > domainEnd) {
                throw new IllegalArgumentException("Wave-pulse inputs must be finite and within their physical domain");
            }
        }
    }
}
