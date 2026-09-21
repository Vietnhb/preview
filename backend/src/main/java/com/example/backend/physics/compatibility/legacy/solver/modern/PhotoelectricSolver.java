package com.example.backend.physics.compatibility.legacy.solver.modern;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.modern.PhotoelectricParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PhotoelectricSolver implements PhysicsSolver {
    @Override public String solverId() { return "photoelectric_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"photoelectric_effect".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported photoelectric model: " + PhysicsValues.model(specification));
        PhotoelectricParameters p = PhotoelectricParameters.from(specification, overrides);
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(Math.max(0.01, durationSeconds) / Math.max(0.001, stepSeconds))));
        List<Double> time = new ArrayList<>(points + 1); Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> photon = new ArrayList<>(points + 1), kinetic = new ArrayList<>(points + 1), stop = new ArrayList<>(points + 1), wavelength = new ArrayList<>(points + 1), emission = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) { time.add(Math.min(Math.max(0.01, durationSeconds), i * Math.max(0.001, stepSeconds))); photon.add(p.photonEnergy()); kinetic.add(p.maximumKineticEnergy()); stop.add(p.stoppingPotential()); wavelength.add(p.wavelength()); emission.add(p.emissionOccurs() ? 1.0 : 0.0); }
        values.put("photonEnergy", photon); values.put("maximumKineticEnergy", kinetic); values.put("stoppingPotential", stop); values.put("wavelength", wavelength); values.put("emissionOccurs", emission);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
