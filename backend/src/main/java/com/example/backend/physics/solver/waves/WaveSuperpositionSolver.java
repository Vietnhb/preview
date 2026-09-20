package com.example.backend.physics.solver.waves;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.WaveFieldGrid;
import com.example.backend.physics.model.waves.WaveSuperpositionParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Samples the linear sum of two coherent right-moving waves. */
@Component
public class WaveSuperpositionSolver implements PhysicsSolver {
    private static final String FIELD_ID = "superpositionDisplacement";

    @Override public String solverId() { return "wave_superposition_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"wave_superposition".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported superposition model: " + PhysicsValues.model(specification));
        }
        WaveSuperpositionParameters parameters = WaveSuperpositionParameters.from(specification, overrides);
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
                "m", "s", new ScalarField.Sampling(dx, stepSeconds), "linear", "open;superposition;components=2;direction=+x");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", displacement); values.put("particleVelocity", velocity); values.put("particleAcceleration", acceleration);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    static State state(WaveSuperpositionParameters parameters, double x, double time) {
        double omega = parameters.angularFrequency();
        double k = parameters.waveNumber();
        double phase1 = k * x - omega * time + parameters.phase1();
        double phase2 = k * x - omega * time + parameters.phase2();
        double displacement = parameters.amplitude1() * Math.cos(phase1) + parameters.amplitude2() * Math.cos(phase2);
        double velocity = omega * (parameters.amplitude1() * Math.sin(phase1) + parameters.amplitude2() * Math.sin(phase2));
        double acceleration = -omega * omega * displacement;
        return new State(displacement, velocity, acceleration);
    }

    public record State(double displacement, double velocity, double acceleration) { }
}
