package com.example.backend.physics.compatibility.legacy.solver.waves;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.waves.StringWaveParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numerical sampler for a transverse string wave. It represents the string as
 * a scalar displacement field, never as a collection of moving fake particles.
 */
@Component
public class StringWaveSolver implements PhysicsSolver {
    private static final String FIELD_ID = "transverseDisplacement";

    @Override
    public String solverId() {
        return "string_wave_solver";
    }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
            double durationSeconds, double stepSeconds) {
        if (!"string_wave".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported string-wave model: " + PhysicsValues.model(specification));
        }
        StringWaveParameters parameters = StringWaveParameters.from(specification, overrides);
        double duration = positiveFinite(durationSeconds, "durationSeconds");
        double step = positiveFinite(stepSeconds, "stepSeconds");
        int timeSamples = timeSamples(duration, step);
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples());

        List<Double> time = timeCoordinates(duration, step, timeSamples);
        List<Double> x = spaceCoordinates(parameters);
        List<List<Double>> fieldValues = new ArrayList<>(timeSamples);
        List<Double> displacement = new ArrayList<>(timeSamples);
        List<Double> particleVelocity = new ArrayList<>(timeSamples);
        List<Double> particleAcceleration = new ArrayList<>(timeSamples);

        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(x.size());
            for (double coordinate : x)
                row.add(state(parameters, coordinate, currentTime).displacement());
            fieldValues.add(row);
            WaveState probe = state(parameters, parameters.probePosition(), currentTime);
            displacement.add(probe.displacement());
            particleVelocity.add(probe.velocity());
            particleAcceleration.add(probe.acceleration());
        }

        double spaceStep = (parameters.domainEnd() - parameters.domainStart()) / (parameters.spatialSamples() - 1d);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.SCALAR_FIELD_TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, fieldValues,
                "m", "s", new ScalarField.Sampling(spaceStep, step), "linear", boundary(parameters));
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", displacement);
        values.put("particleVelocity", particleVelocity);
        values.put("particleAcceleration", particleAcceleration);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    private static WaveState state(StringWaveParameters parameters, double x, double time) {
        double omega = parameters.angularFrequency();
        if (StringWaveParameters.PERIODIC.equals(parameters.sourceBehavior())) {
            double phase = parameters.waveNumber() * x - omega * time + parameters.phase();
            double displacement = parameters.amplitude() * Math.cos(phase);
            return new WaveState(displacement, parameters.amplitude() * omega * Math.sin(phase),
                    -parameters.amplitude() * omega * omega * Math.cos(phase));
        }

        // A source-started wave is a delayed source signal. Points with x > c*t
        // are exactly undisturbed. The quintic envelope is C2 at both ends.
        double delayedTime = time - x / parameters.waveSpeed();
        if (delayedTime < 0)
            return WaveState.ZERO;
        Envelope envelope = quinticEnvelope(delayedTime, parameters.sourceRampSeconds());
        double phase = parameters.phase() - omega * delayedTime;
        double cosine = Math.cos(phase);
        double sine = Math.sin(phase);
        double amplitude = parameters.amplitude();
        return new WaveState(amplitude * envelope.value() * cosine,
                amplitude * (envelope.firstDerivative() * cosine + envelope.value() * omega * sine),
                amplitude * (envelope.secondDerivative() * cosine + 2 * envelope.firstDerivative() * omega * sine
                        - envelope.value() * omega * omega * cosine));
    }

    private static Envelope quinticEnvelope(double time, double rampSeconds) {
        if (time >= rampSeconds)
            return Envelope.ONE;
        double normalized = Math.max(0, time / rampSeconds);
        double value = 6 * Math.pow(normalized, 5) - 15 * Math.pow(normalized, 4) + 10 * Math.pow(normalized, 3);
        double first = 30 * normalized * normalized * Math.pow(1 - normalized, 2) / rampSeconds;
        double second = 60 * normalized * (2 * normalized * normalized - 3 * normalized + 1)
                / (rampSeconds * rampSeconds);
        return new Envelope(value, first, second);
    }

    private static List<Double> timeCoordinates(double duration, double step, int samples) {
        List<Double> result = new ArrayList<>(samples);
        for (int index = 0; index < samples; index++)
            result.add(Math.min(duration, index * step));
        return result;
    }

    private static List<Double> spaceCoordinates(StringWaveParameters parameters) {
        double spaceStep = (parameters.domainEnd() - parameters.domainStart()) / (parameters.spatialSamples() - 1d);
        List<Double> result = new ArrayList<>(parameters.spatialSamples());
        for (int index = 0; index < parameters.spatialSamples(); index++) {
            result.add(index == parameters.spatialSamples() - 1 ? parameters.domainEnd()
                    : parameters.domainStart() + index * spaceStep);
        }
        return result;
    }

    private static int timeSamples(double duration, double step) {
        double intervals = Math.ceil(duration / step);
        if (!Double.isFinite(intervals) || intervals > ScalarField.MAX_TIME_SAMPLES - 1L) {
            throw new IllegalArgumentException("Time sampling exceeds scalar-field resource limits");
        }
        return (int) intervals + 1;
    }

    private static double positiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException(label + " must be finite and positive");
        return value;
    }

    private static String boundary(StringWaveParameters parameters) {
        if (StringWaveParameters.PERIODIC.equals(parameters.sourceBehavior()))
            return "open";
        return "open;source_started;direction=+x;ramp=quintic_smoothstep:" + parameters.sourceRampSeconds() + "s";
    }

    private record WaveState(double displacement, double velocity, double acceleration) {
        private static final WaveState ZERO = new WaveState(0, 0, 0);
    }

    private record Envelope(double value, double firstDerivative, double secondDerivative) {
        private static final Envelope ONE = new Envelope(1, 0, 0);
    }
}
