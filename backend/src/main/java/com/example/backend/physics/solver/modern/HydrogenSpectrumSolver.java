package com.example.backend.physics.solver.modern;

import com.example.backend.physics.solver.thermal.TemperatureScaleSolver;

import com.example.backend.physics.solver.PhysicsSolver;

import com.example.backend.physics.model.modern.HydrogenSpectrumParameters;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HydrogenSpectrumSolver implements PhysicsSolver {
    @Override public String solverId() { return "hydrogen_spectrum_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"atomic_spectra".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported atomic-spectrum model: " + PhysicsValues.model(specification));
        HydrogenSpectrumParameters p = HydrogenSpectrumParameters.from(specification, overrides);
        List<Double> time = TemperatureScaleSolver.staticTime(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("wavelength", java.util.Collections.nCopies(time.size(), p.wavelength()));
        values.put("frequency", java.util.Collections.nCopies(time.size(), p.frequency()));
        values.put("photonEnergy", java.util.Collections.nCopies(time.size(), p.photonEnergy()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
