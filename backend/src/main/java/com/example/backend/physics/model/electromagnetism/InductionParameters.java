package com.example.backend.physics.model.electromagnetism;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical inputs for a uniform-field Faraday induction experiment. */
public record InductionParameters(double turns, double magneticField, double coilArea,
                                  double fieldRate, double coilAngle) {
    public static InductionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double turns = PhysicsValues.require(specification, overrides, "turns");
        double field = PhysicsValues.require(specification, overrides, "magnetic_field");
        double area = PhysicsValues.require(specification, overrides, "coil_area");
        double rate = PhysicsValues.require(specification, overrides, "magnetic_field_rate");
        double angle = PhysicsValues.require(specification, overrides, "coil_angle");
        if (!(turns >= 1 && turns == Math.rint(turns) && area > 0) || !Double.isFinite(field) || !Double.isFinite(rate)
                || !Double.isFinite(angle) || angle < 0 || angle > Math.PI) {
            throw new IllegalArgumentException("Turns must be a positive integer; coil geometry and field rate are invalid");
        }
        return new InductionParameters(turns, field, area, rate, angle);
    }
    public double flux() { return magneticField * coilArea * Math.cos(coilAngle); }
    public double inducedEmf() { return -turns * coilArea * Math.cos(coilAngle) * fieldRate; }
}
