package com.example.backend.physics.model.thermal;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical sign convention: Delta U = Q - W, where W is work done by the system. */
public record FirstLawParameters(double initialInternalEnergy, double heatAdded, double workDone) {
    public static FirstLawParameters from(JsonNode specification, Map<String, Double> overrides) {
        double u0 = PhysicsValues.require(specification, overrides, "initial_internal_energy");
        double q = PhysicsValues.require(specification, overrides, "heat_added");
        double w = PhysicsValues.require(specification, overrides, "work_done");
        if (!Double.isFinite(u0) || !Double.isFinite(q) || !Double.isFinite(w)) {
            throw new IllegalArgumentException("Thermodynamic energies must be finite");
        }
        return new FirstLawParameters(u0, q, w);
    }
    public double deltaInternalEnergy() { return heatAdded - workDone; }
    public double finalInternalEnergy() { return initialInternalEnergy + deltaInternalEnergy(); }
}
