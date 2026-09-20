package com.example.backend.physics.solver.modern;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.modern.DeBroglieDiffractionParameters;
import com.example.backend.physics.solver.PhysicsSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Solver for quantum extension models that are not part of photoelectric effect. */
@Component
public class QuantumExtensionSolver implements PhysicsSolver {
    @Override public String solverId() { return "quantum_extension_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"de_broglie_diffraction".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported quantum extension model: " + PhysicsValues.model(specification));
        }
        DeBroglieDiffractionParameters p = DeBroglieDiffractionParameters.from(specification, overrides);
        List<Double> time = List.of(0d);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("momentum", Collections.singletonList(p.momentum()));
        values.put("wavelength", Collections.singletonList(p.wavelength()));
        values.put("kineticEnergy", Collections.singletonList(p.kineticEnergy()));
        values.put("diffractionAngle", Collections.singletonList(p.diffractionAngle()));
        values.put("diffractionAllowed", Collections.singletonList(p.diffractionAllowed() ? 1d : 0d));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
