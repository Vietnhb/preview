package com.example.backend.physics.compatibility.legacy.solver.thermal;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.thermal.IdealGasParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Analytical time-parameterized isothermal compression/expansion. */
@Component
public class IdealGasSolver implements PhysicsSolver {
    @Override public String solverId() { return "ideal_gas_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"ideal_gas_isothermal".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported ideal-gas model: " + PhysicsValues.model(specification));
        }
        IdealGasParameters parameters = IdealGasParameters.from(specification, overrides);
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0 || !Double.isFinite(stepSeconds) || stepSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        int points = (int) Math.ceil(durationSeconds / stepSeconds);
        if (points > 16_383) throw new IllegalArgumentException("Ideal-gas sampling exceeds resource limits");
        List<Double> time = new ArrayList<>(points + 1);
        List<Double> volume = new ArrayList<>(points + 1);
        List<Double> pressure = new ArrayList<>(points + 1);
        List<Double> temperature = new ArrayList<>(points + 1);
        List<Double> work = new ArrayList<>(points + 1);
        double scale = parameters.pressureScale();
        for (int index = 0; index <= points; index++) {
            double current = Math.min(durationSeconds, index * stepSeconds);
            double currentVolume = parameters.initialVolume() + parameters.volumeRate() * current;
            if (currentVolume <= 0) throw new IllegalArgumentException("Ideal-gas volume reaches zero within the simulation horizon");
            double currentPressure = scale / currentVolume;
            time.add(current); volume.add(currentVolume); pressure.add(currentPressure);
            temperature.add(parameters.temperature());
            work.add(scale * Math.log(currentVolume / parameters.initialVolume()));
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("volume", volume); values.put("pressure", pressure);
        values.put("temperature", temperature); values.put("work", work);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
