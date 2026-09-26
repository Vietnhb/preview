package com.example.backend.physics.compatibility.legacy.solver.optics;

import com.example.backend.physics.runtime.SimulationTimeline;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.model.optics.AstronomicalTelescopeParameters;
import com.example.backend.physics.compatibility.legacy.model.optics.CompoundMicroscopeParameters;
import com.example.backend.physics.compatibility.legacy.model.optics.SimpleMagnifierParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates paraxial high-school optical-instrument models as static timeseries. */
@Component
public class OpticalInstrumentSolver implements PhysicsSolver {
    @Override public String solverId() { return "optical_instrument_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        String model = PhysicsValues.model(specification);
        List<Double> time = SimulationTimeline.sample(durationSeconds, stepSeconds);
        Map<String, Double> point = evaluate(model, specification, overrides);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        point.forEach((key, value) -> values.put(key, java.util.Collections.nCopies(time.size(), value)));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    public static Map<String, Double> evaluate(String model, JsonNode specification, Map<String, Double> overrides) {
        return switch (model) {
            case "simple_magnifier" -> {
                SimpleMagnifierParameters p = SimpleMagnifierParameters.from(specification, overrides);
                yield Map.of("virtualImageDistance", p.virtualImageDistance(),
                        "linearMagnification", p.linearMagnification(),
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
        };
    }
}
