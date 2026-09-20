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

/** Typed Gaussian pulse reflection module with an independent image-pulse oracle. */
public final class WaveReflectionModule implements PhysicsModule<WaveReflectionModule.Parameters> {
    public static final String MODULE_ID = "wave_reflection";
    public static final String NUMERICAL_SOLVER_ID = "wave_reflection_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "wave_reflection_reference_v2";
    private static final String FIELD_ID = "reflectionDisplacement";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double amplitude = requireCanonical(quantities, "amplitude", "m");
        double speed = requireCanonical(quantities, "wave_speed", "m/s");
        double width = requireCanonical(quantities, "pulse_width", "m");
        double initialPosition = requireCanonical(quantities, "initial_position", "m");
        double boundaryPosition = requireCanonical(quantities, "boundary_position", "m");
        double domainStart = requireCanonical(quantities, "domain_start", "m");
        double rawSamples = requireCanonical(quantities, "spatial_samples", "1");
        double probePosition = requireCanonical(quantities, "probe_position", "m");
        double reflectionCoefficient = requireCanonical(quantities, "reflection_coefficient", "1");
        if (rawSamples != Math.rint(rawSamples) || rawSamples < 2.0
                || rawSamples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("Reflection spatial_samples must be an integer within scalar-field limits");
        }
        return new Parameters(amplitude, speed, width, initialPosition, boundaryPosition, domainStart,
                (int) rawSamples, probePosition, reflectionCoefficient);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        int timeSamples = WaveFieldGrid.timeSamples(clock.durationSeconds(), clock.stepSeconds());
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples());
        validateSampling(parameters, clock);

        List<Double> time = WaveFieldGrid.timeCoordinates(clock.durationSeconds(), clock.stepSeconds(), timeSamples);
        List<Double> x = WaveFieldGrid.spaceCoordinates(
                parameters.domainStart(), parameters.boundaryPosition(), parameters.spatialSamples());
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> displacement = new ArrayList<>(timeSamples);
        List<Double> velocity = new ArrayList<>(timeSamples);
        List<Double> acceleration = new ArrayList<>(timeSamples);
        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(x.size());
            for (double coordinate : x) row.add(numericalState(parameters, coordinate, currentTime).displacement());
            rows.add(List.copyOf(row));
            State probe = numericalState(parameters, parameters.probePosition(), currentTime);
            displacement.add(probe.displacement());
            velocity.add(probe.velocity());
            acceleration.add(probe.acceleration());
        }

        double dx = (parameters.boundaryPosition() - parameters.domainStart())
                / (parameters.spatialSamples() - 1.0);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "m", "s", new ScalarField.Sampling(dx, clock.stepSeconds()), "linear",
                "open;reflection;coefficient=" + parameters.reflectionCoefficient());
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
            throw new IllegalArgumentException("Reflection reference time must be finite and non-negative");
        }
        double incidentOffset = parameters.probePosition()
                - (parameters.initialPosition() + parameters.waveSpeed() * timeSeconds);
        double reflectedOffset = (2.0 * parameters.boundaryPosition() - parameters.probePosition())
                - (parameters.initialPosition() + parameters.waveSpeed() * timeSeconds);
        double widthSquared = parameters.width() * parameters.width();
        double incident = parameters.amplitude()
                * Math.exp(-square(incidentOffset / parameters.width()));
        double reflected = parameters.amplitude()
                * Math.exp(-square(reflectedOffset / parameters.width()));
        double incidentVelocity = 2.0 * parameters.waveSpeed() * incidentOffset / widthSquared * incident;
        double reflectedVelocity = 2.0 * parameters.waveSpeed() * reflectedOffset / widthSquared * reflected;
        double incidentAcceleration = ((4.0 * square(incidentOffset) / square(widthSquared))
                - 2.0 / widthSquared) * square(parameters.waveSpeed()) * incident;
        double reflectedAcceleration = ((4.0 * square(reflectedOffset) / square(widthSquared))
                - 2.0 / widthSquared) * square(parameters.waveSpeed()) * reflected;
        double displacement = incident + parameters.reflectionCoefficient() * reflected;
        double velocity = incidentVelocity + parameters.reflectionCoefficient() * reflectedVelocity;
        double acceleration = incidentAcceleration + parameters.reflectionCoefficient() * reflectedAcceleration;
        requireFinite(displacement, "reference displacement");
        requireFinite(velocity, "reference particle velocity");
        requireFinite(acceleration, "reference particle acceleration");
        return new AnalyticalPoint(Map.of("displacement", displacement,
                "particleVelocity", velocity, "particleAcceleration", acceleration));
    }

    private static State numericalState(Parameters parameters, double x, double time) {
        double incidentQ = (x - parameters.initialPosition() - parameters.waveSpeed() * time)
                / parameters.width();
        double reflectedQ = (2.0 * parameters.boundaryPosition() - x - parameters.initialPosition()
                - parameters.waveSpeed() * time) / parameters.width();
        double speedOverWidth = parameters.waveSpeed() / parameters.width();
        State incident = gaussian(parameters.amplitude(), incidentQ, speedOverWidth);
        State reflected = gaussian(parameters.amplitude(), reflectedQ, speedOverWidth);
        return new State(incident.displacement() + parameters.reflectionCoefficient() * reflected.displacement(),
                incident.velocity() + parameters.reflectionCoefficient() * reflected.velocity(),
                incident.acceleration() + parameters.reflectionCoefficient() * reflected.acceleration());
    }

    private static State gaussian(double amplitude, double q, double speedOverWidth) {
        double envelope = Math.exp(-q * q);
        double displacement = amplitude * envelope;
        double velocity = displacement * 2.0 * q * speedOverWidth;
        double acceleration = displacement * (4.0 * q * q - 2.0) * speedOverWidth * speedOverWidth;
        requireFinite(displacement, "displacement");
        requireFinite(velocity, "particle velocity");
        requireFinite(acceleration, "particle acceleration");
        return new State(displacement, velocity, acceleration);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical reflection quantity " + key
                    + " must use unit " + expectedUnit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Reflection quantity must be finite: " + key);
        return value;
    }

    private static void validateSampling(Parameters parameters, SimulationClock clock) {
        double dx = (parameters.boundaryPosition() - parameters.domainStart())
                / (parameters.spatialSamples() - 1.0);
        double spatialSamplesPerWidth = parameters.width() / dx;
        double temporalSamplesPerWidth = parameters.width() / parameters.waveSpeed() / clock.stepSeconds();
        if (!Double.isFinite(dx) || dx <= 0.0 || !Double.isFinite(spatialSamplesPerWidth)
                || spatialSamplesPerWidth < 4.0 || !Double.isFinite(temporalSamplesPerWidth)
                || temporalSamplesPerWidth < 4.0) {
            throw new IllegalArgumentException(
                    "Reflection grids must provide at least four samples per pulse width in space and time");
        }
    }

    private static double square(double value) { return value * value; }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Reflection " + label + " must be finite");
    }

    private record State(double displacement, double velocity, double acceleration) { }

    /** SI quantities already resolved from the pinned compiled schema. */
    public record Parameters(double amplitude, double waveSpeed, double width, double initialPosition,
                             double boundaryPosition, double domainStart, int spatialSamples,
                             double probePosition, double reflectionCoefficient) {
        public Parameters {
            if (!Double.isFinite(amplitude) || amplitude < 0.0
                    || !Double.isFinite(waveSpeed) || waveSpeed <= 0.0
                    || !Double.isFinite(width) || width <= 0.0
                    || !Double.isFinite(initialPosition) || !Double.isFinite(boundaryPosition)
                    || !Double.isFinite(domainStart) || boundaryPosition <= domainStart
                    || initialPosition < domainStart || initialPosition > boundaryPosition
                    || spatialSamples < 2 || spatialSamples > ScalarField.MAX_SPATIAL_SAMPLES
                    || !Double.isFinite(probePosition) || probePosition < domainStart
                    || probePosition > boundaryPosition
                    || !Double.isFinite(reflectionCoefficient)
                    || reflectionCoefficient < -1.0 || reflectionCoefficient > 1.0) {
                throw new IllegalArgumentException(
                        "Reflection quantities must be finite and within the boundary and coefficient domains");
            }
        }
    }
}
