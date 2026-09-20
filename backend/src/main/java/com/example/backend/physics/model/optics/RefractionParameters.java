package com.example.backend.physics.model.optics;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical inputs for a planar-interface Snell refraction ray. */
public record RefractionParameters(double refractiveIndex1, double refractiveIndex2, double incidentAngle) {
    public static RefractionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double n1 = PhysicsValues.require(specification, overrides, "refractive_index_1");
        double n2 = PhysicsValues.require(specification, overrides, "refractive_index_2");
        double angle = PhysicsValues.require(specification, overrides, "incident_angle");
        if (!(n1 > 0 && n2 > 0) || !Double.isFinite(angle) || angle < 0 || angle > Math.PI / 2) {
            throw new IllegalArgumentException("Refractive indices must be positive and incident angle in [0, pi/2]");
        }
        return new RefractionParameters(n1, n2, angle);
    }
    public double sineOfRefractedAngle() { return refractiveIndex1 * Math.sin(incidentAngle) / refractiveIndex2; }
    public boolean totalInternalReflection() { return sineOfRefractedAngle() > 1.0; }
    /** Returns zero when no transmitted ray exists; callers must inspect totalInternalReflection(). */
    public double refractedAngle() { return totalInternalReflection() ? 0.0 : Math.asin(sineOfRefractedAngle()); }
}
