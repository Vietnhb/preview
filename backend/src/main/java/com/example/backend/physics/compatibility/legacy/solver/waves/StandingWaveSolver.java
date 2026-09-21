package com.example.backend.physics.compatibility.legacy.solver.waves;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.waves.StandingWaveParameters;
import com.example.backend.physics.model.WaveFieldGrid;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StandingWaveSolver implements PhysicsSolver {
    private static final String FIELD_ID = "standingDisplacement";

    @Override public String solverId() { return "standing_wave_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"standing_wave".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported standing-wave model: " + PhysicsValues.model(specification));
        }
        StandingWaveParameters parameters = StandingWaveParameters.from(specification, overrides);
        int timeSamples = WaveFieldGrid.timeSamples(durationSeconds, stepSeconds);
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples());
        List<Double> time = WaveFieldGrid.timeCoordinates(durationSeconds, stepSeconds, timeSamples);
        List<Double> x = WaveFieldGrid.spaceCoordinates(parameters.domainStart(), parameters.domainEnd(), parameters.spatialSamples());
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> displacement = new ArrayList<>(timeSamples);
        List<Double> velocity = new ArrayList<>(timeSamples);
        List<Double> acceleration = new ArrayList<>(timeSamples);
        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(x.size());
            for (double coordinate : x) row.add(state(parameters, coordinate, currentTime).displacement());
            rows.add(row);
            State probe = state(parameters, parameters.probePosition(), currentTime);
            displacement.add(probe.displacement()); velocity.add(probe.velocity()); acceleration.add(probe.acceleration());
        }
        double dx = (parameters.domainEnd() - parameters.domainStart()) / (parameters.spatialSamples() - 1d);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "m", "s", new ScalarField.Sampling(dx, stepSeconds), "linear", "open;standing;counter_propagating");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", displacement); values.put("particleVelocity", velocity); values.put("particleAcceleration", acceleration);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    static State state(StandingWaveParameters parameters, double x, double time) {
        double spatial = parameters.waveNumber() * (x - parameters.domainStart());
        double temporal = parameters.angularFrequency() * time + parameters.phase();
        double shape = Math.sin(spatial);
        double displacement = parameters.amplitude() * shape * Math.cos(temporal);
        double velocity = -parameters.amplitude() * shape * parameters.angularFrequency() * Math.sin(temporal);
        double acceleration = -parameters.angularFrequency() * parameters.angularFrequency() * displacement;
        return new State(displacement, velocity, acceleration);
    }

    public record State(double displacement, double velocity, double acceleration) { }
}
