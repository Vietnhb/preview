package com.example.backend.physics.reference.dynamics;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.dynamics.CircularMotionParameters;
import com.example.backend.physics.model.dynamics.GravityOrbitParameters;
import com.example.backend.physics.model.dynamics.HookeLawParameters;
import com.example.backend.physics.model.dynamics.HydrostaticsParameters;
import com.example.backend.physics.model.dynamics.WorkEnergyParameters;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independent closed-form oracle for mechanics foundation models. */
@Component
public class MechanicsFoundationReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "mechanics_foundation_reference"; }

    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        String model = PhysicsValues.model(specification);
        Map<String, Double> values = new LinkedHashMap<>();
        switch (model) {
            case "work_energy_power" -> {
                WorkEnergyParameters p = WorkEnergyParameters.from(specification, overrides);
                values.put("workByForce", p.workByForce()); values.put("initialKineticEnergy", p.initialKineticEnergy());
                values.put("finalKineticEnergy", p.finalKineticEnergy()); values.put("deltaKineticEnergy", p.deltaKineticEnergy());
                values.put("averagePower", p.averagePower());
            }
            case "circular_motion" -> {
                CircularMotionParameters p = CircularMotionParameters.from(specification, overrides);
                values.put("angularSpeed", p.angularSpeed()); values.put("period", p.period()); values.put("frequency", p.frequency());
                values.put("centripetalAcceleration", p.centripetalAcceleration()); values.put("centripetalForce", p.centripetalForce());
            }
            case "hooke_law" -> { HookeLawParameters p = HookeLawParameters.from(specification, overrides); values.put("restoringForce", p.restoringForce()); values.put("elasticPotentialEnergy", p.elasticPotentialEnergy()); }
            case "gravity_orbit" -> { GravityOrbitParameters p = GravityOrbitParameters.from(specification, overrides); values.put("gravitationalForce", p.gravitationalForce()); values.put("gravitationalField", p.gravitationalField()); values.put("orbitalSpeed", p.orbitalSpeed()); values.put("orbitalPeriod", p.orbitalPeriod()); }
            case "hydrostatics" -> { HydrostaticsParameters p = HydrostaticsParameters.from(specification, overrides); values.put("gaugePressure", p.gaugePressure()); values.put("absolutePressure", p.absolutePressure()); values.put("buoyantForce", p.buoyantForce()); }
            default -> throw new IllegalArgumentException("Unsupported mechanics model: " + model);
        }
        return new AnalyticalPoint(values);
    }
}
