package com.example.backend.physics.compatibility.legacy.solver.waves;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.compatibility.legacy.model.waves.SoundWaveParameters;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.WaveFieldGrid;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Samples a travelling acoustic pressure wave on the default canvas plane. */
@Component
public class SoundWaveSolver implements PhysicsSolver {
    private static final String FIELD_ID = "soundPressure";

    @Override public String solverId() { return "sound_wave_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"sound_wave".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported sound-wave model: " + PhysicsValues.model(specification));
        }
        SoundWaveParameters parameters = SoundWaveParameters.from(specification, overrides);
        int timeSamples = WaveFieldGrid.timeSamples(durationSeconds, stepSeconds);
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples());
        List<Double> time = WaveFieldGrid.timeCoordinates(durationSeconds, stepSeconds, timeSamples);
        List<Double> x = WaveFieldGrid.spaceCoordinates(parameters.domainStart(), parameters.domainEnd(), parameters.spatialSamples());
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> pressure = new ArrayList<>(timeSamples);
        List<Double> pressureRate = new ArrayList<>(timeSamples);
        List<Double> pressureAcceleration = new ArrayList<>(timeSamples);
        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(x.size());
            for (double coordinate : x) row.add(state(parameters, coordinate, currentTime).pressure());
            rows.add(row);
            State probe = state(parameters, parameters.probePosition(), currentTime);
            pressure.add(probe.pressure()); pressureRate.add(probe.rate()); pressureAcceleration.add(probe.acceleration());
        }
        double dx = (parameters.domainEnd() - parameters.domainStart()) / (parameters.spatialSamples() - 1d);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "Pa", "s", new ScalarField.Sampling(dx, stepSeconds), "linear", "open;acoustic_pressure;direction=+x");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("pressure", pressure); values.put("pressureRate", pressureRate); values.put("pressureAcceleration", pressureAcceleration);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    static State state(SoundWaveParameters parameters, double x, double time) {
        double angle = parameters.waveNumber() * x - parameters.angularFrequency() * time + parameters.phase();
        double pressure = parameters.pressureAmplitude() * Math.cos(angle);
        double rate = parameters.pressureAmplitude() * parameters.angularFrequency() * Math.sin(angle);
        return new State(pressure, rate, -parameters.angularFrequency() * parameters.angularFrequency() * pressure);
    }

    public record State(double pressure, double rate, double acceleration) { }
}
