package com.example.backend.physics.reference.optics;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.optics.AstronomicalTelescopeParameters;
import com.example.backend.physics.model.optics.CompoundMicroscopeParameters;
import com.example.backend.physics.model.optics.SimpleMagnifierParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent reference evaluator for paraxial optical instruments. */
@Component
public class OpticalInstrumentReferenceSolver implements ReferenceSolver {
    @Override
    public String solverId() {
        return "optical_instrument_reference";
    }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        String model = PhysicsValues.model(specification);
        return new AnalyticalPoint(switch (model) {
            case "simple_magnifier" -> {
                SimpleMagnifierParameters p = SimpleMagnifierParameters.from(specification, overrides);
                yield Map.of("virtualImageDistance", p.virtualImageDistance(), "linearMagnification",
                        p.linearMagnification(),
                        "relaxedAngularMagnification", p.relaxedAngularMagnification(),
                        "nearPointAngularMagnification", p.nearPointAngularMagnification());
            }
            case "compound_microscope" -> {
                CompoundMicroscopeParameters p = CompoundMicroscopeParameters.from(specification, overrides);
                yield Map.of("objectiveMagnification", p.objectiveMagnification(),
                        "relaxedAngularMagnification", p.relaxedAngularMagnification(),
                        "nearPointAngularMagnification", p.nearPointAngularMagnification());
            }
            case "astronomical_telescope" -> {
                AstronomicalTelescopeParameters p = AstronomicalTelescopeParameters.from(specification, overrides);
                yield Map.of("angularMagnification", p.angularMagnification(),
                        "signedAngularMagnification", p.signedAngularMagnification(),
                        "normalAdjustmentLength", p.normalAdjustmentLength());
            }
            default -> throw new IllegalArgumentException("Unsupported optical-instrument model: " + model);
        });
    }
}
