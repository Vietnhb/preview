package com.example.backend.physics.compatibility.legacy.reference.waves;

import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.waves.StringWaveParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Independent analytical evaluator for the configured string-wave probe.
 * It intentionally does not call the numerical field sampler.
 */
@Component
public class StringWaveReferenceSolver implements ReferenceSolver {
    @Override
    public String solverId() {
        return "string_wave_reference";
    }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"string_wave".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException(
                    "Unsupported string-wave reference model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds))
            throw new IllegalArgumentException("timeSeconds must be finite");
        StringWaveParameters parameters = StringWaveParameters.from(specification, overrides);
        ReferenceState state = referenceState(parameters, parameters.probePosition(), Math.max(0, timeSeconds));
        return new AnalyticalPoint(Map.of(
                "displacement", state.displacement(),
                "particleVelocity", state.velocity(),
                "particleAcceleration", state.acceleration()));
    }

    private static ReferenceState referenceState(StringWaveParameters parameters, double x, double time) {
        double angularFrequency = 2 * Math.PI * parameters.frequency();
        if (StringWaveParameters.PERIODIC.equals(parameters.sourceBehavior())) {
            double wavelength = parameters.waveSpeed() / parameters.frequency();
            double phase = 2 * Math.PI * x / wavelength - angularFrequency * time + parameters.phase();
            double displacement = parameters.amplitude() * Math.cos(phase);
            return new ReferenceState(displacement, parameters.amplitude() * angularFrequency * Math.sin(phase),
                    -parameters.amplitude() * angularFrequency * angularFrequency * Math.cos(phase));
        }

        double delayedTime = time - x / parameters.waveSpeed();
        if (delayedTime < 0)
            return ReferenceState.ZERO;
        Ramp ramp = ramp(delayedTime, parameters.sourceRampSeconds());
        double phase = parameters.phase() - angularFrequency * delayedTime;
        double cosine = Math.cos(phase);
        double sine = Math.sin(phase);
        double amplitude = parameters.amplitude();
        return new ReferenceState(amplitude * ramp.value() * cosine,
                amplitude * (ramp.firstDerivative() * cosine + ramp.value() * angularFrequency * sine),
                amplitude * (ramp.secondDerivative() * cosine + 2 * ramp.firstDerivative() * angularFrequency * sine
                        - ramp.value() * angularFrequency * angularFrequency * cosine));
    }

    private static Ramp ramp(double delayedTime, double rampSeconds) {
        if (delayedTime >= rampSeconds)
            return Ramp.ONE;
        double s = Math.max(0, delayedTime / rampSeconds);
        double value = ((6 * s - 15) * s + 10) * s * s * s;
        double first = 30 * s * s * (1 - s) * (1 - s) / rampSeconds;
        double second = 60 * s * (2 * s * s - 3 * s + 1) / (rampSeconds * rampSeconds);
        return new Ramp(value, first, second);
    }

    private record ReferenceState(double displacement, double velocity, double acceleration) {
        private static final ReferenceState ZERO = new ReferenceState(0, 0, 0);
    }

    private record Ramp(double value, double firstDerivative, double secondDerivative) {
        private static final Ramp ONE = new Ramp(1, 0, 0);
    }
}
