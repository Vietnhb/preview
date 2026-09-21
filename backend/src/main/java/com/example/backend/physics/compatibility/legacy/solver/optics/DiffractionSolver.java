package com.example.backend.physics.compatibility.legacy.solver.optics;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.optics.DiffractionParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiffractionSolver implements PhysicsSolver {
    @Override public String solverId() { return "diffraction_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides, double duration, double step) {
        if (!"diffraction_polarization".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported diffraction model: " + PhysicsValues.model(specification));
        DiffractionParameters p = DiffractionParameters.from(specification, overrides);
        List<Double> time = SimulationTimeline.sample(duration, step);
        Map<String,List<Double>> values = new LinkedHashMap<>();
        values.put("diffractionAngle", java.util.Collections.nCopies(time.size(), p.diffractionAngle()));
        values.put("minimumExists", java.util.Collections.nCopies(time.size(), p.minimumExists() ? 1.0 : 0.0));
        values.put("transmittedIntensity", java.util.Collections.nCopies(time.size(), p.transmittedIntensity()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
