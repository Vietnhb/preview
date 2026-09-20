package com.example.backend.physics.solver.circuits;

import com.example.backend.physics.solver.thermal.TemperatureScaleSolver;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.circuits.AcWaveformParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AcWaveformSolver implements PhysicsSolver {
    @Override
    public String solverId() {
        return "ac_waveform_solver";
    }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"ac_waveform".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported AC waveform model: " + PhysicsValues.model(specification));
        AcWaveformParameters p = AcWaveformParameters.from(specification, overrides);
        List<Double> time = TemperatureScaleSolver.staticTime(duration, step);
        List<Double> voltage = new ArrayList<>(time.size());
        for (double t : time)
            voltage.add(p.voltage(t));
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("voltage", voltage);
        values.put("rmsVoltage", java.util.Collections.nCopies(time.size(), p.rmsVoltage()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
