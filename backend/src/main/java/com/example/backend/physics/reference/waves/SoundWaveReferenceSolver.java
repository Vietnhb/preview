package com.example.backend.physics.reference.waves;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.waves.SoundWaveParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent analytical evaluator for the acoustic pressure probe. */
@Component
public class SoundWaveReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "sound_wave_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"sound_wave".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported sound-wave reference model: " + PhysicsValues.model(specification));
        }
        if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("timeSeconds must be finite");
        SoundWaveParameters parameters = SoundWaveParameters.from(specification, overrides);
        double time = Math.max(0, timeSeconds);
        double angle = parameters.waveNumber() * parameters.probePosition()
                - parameters.angularFrequency() * time + parameters.phase();
        double pressure = parameters.pressureAmplitude() * Math.cos(angle);
        double rate = parameters.pressureAmplitude() * parameters.angularFrequency() * Math.sin(angle);
        return new AnalyticalPoint(Map.of("pressure", pressure, "pressureRate", rate,
                "pressureAcceleration", -parameters.angularFrequency() * parameters.angularFrequency() * pressure));
    }
}
