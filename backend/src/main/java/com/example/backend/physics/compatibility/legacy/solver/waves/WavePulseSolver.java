package com.example.backend.physics.compatibility.legacy.solver.waves;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.WaveFieldGrid;
import com.example.backend.physics.compatibility.legacy.model.waves.WavePulseParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WavePulseSolver implements PhysicsSolver {
    private static final String FIELD_ID = "pulseDisplacement";

    @Override public String solverId() { return "wave_pulse_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"wave_pulse".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported pulse model: " + PhysicsValues.model(specification));
        }
        WavePulseParameters parameters = WavePulseParameters.from(specification, overrides);
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
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.SCALAR_FIELD_TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", x)), List.of(time.size(), x.size()), time, rows,
                "m", "s", new ScalarField.Sampling(dx, stepSeconds), "linear", "open;travelling_pulse;direction=+x");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("displacement", displacement); values.put("particleVelocity", velocity); values.put("particleAcceleration", acceleration);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    static State state(WavePulseParameters parameters, double x, double time) {
        double q = (x - parameters.initialPosition() - parameters.waveSpeed() * time) / parameters.width();
        double envelope = Math.exp(-q * q);
        double cOverWidth = parameters.waveSpeed() / parameters.width();
        double displacement = parameters.amplitude() * envelope;
        double velocity = parameters.amplitude() * envelope * 2 * q * cOverWidth;
        double acceleration = parameters.amplitude() * envelope * (4 * q * q - 2) * cOverWidth * cOverWidth;
        return new State(displacement, velocity, acceleration);
    }

    public record State(double displacement, double velocity, double acceleration) { }
}
