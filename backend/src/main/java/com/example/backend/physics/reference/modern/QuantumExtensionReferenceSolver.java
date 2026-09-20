package com.example.backend.physics.reference.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.modern.DeBroglieDiffractionParameters;
import com.example.backend.physics.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent oracle for de Broglie wavelength and Bragg diffraction. */
@Component
public class QuantumExtensionReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "quantum_extension_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"de_broglie_diffraction".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported quantum extension model: " + PhysicsValues.model(specification));
        }
        DeBroglieDiffractionParameters p = DeBroglieDiffractionParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of(
                "momentum", p.momentum(),
                "wavelength", p.wavelength(),
                "kineticEnergy", p.kineticEnergy(),
                "diffractionAngle", p.diffractionAngle(),
                "diffractionAllowed", p.diffractionAllowed() ? 1d : 0d));
    }
}
