package com.example.backend.physics.model.modern;

import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Apparent-diameter and alignment model for solar/lunar eclipse lessons. */
public record EclipseGeometryParameters(double starRadius, double starDistance,
                                        double occluderRadius, double occluderDistance,
                                        double alignmentAngle) {
    public static EclipseGeometryParameters from(JsonNode specification, Map<String, Double> overrides) {
        double starRadius = PhysicsValues.require(specification, overrides, "star_radius");
        double starDistance = PhysicsValues.require(specification, overrides, "star_distance");
        double occluderRadius = PhysicsValues.require(specification, overrides, "occluder_radius");
        double occluderDistance = PhysicsValues.require(specification, overrides, "occluder_distance");
        double alignment = PhysicsValues.require(specification, overrides, "alignment_angle");
        if (!(starRadius > 0) || !(occluderRadius > 0) || !(starDistance > starRadius)
                || !(occluderDistance > occluderRadius) || alignment < 0) {
            throw new IllegalArgumentException("Eclipse geometry parameters are invalid");
        }
        return new EclipseGeometryParameters(starRadius, starDistance, occluderRadius, occluderDistance, alignment);
    }
    public double starAngularDiameter() { return 2 * Math.atan(starRadius / starDistance); }
    public double occluderAngularDiameter() { return 2 * Math.atan(occluderRadius / occluderDistance); }
    public double alignmentMargin() { return occluderAngularDiameter() - starAngularDiameter() - alignmentAngle; }
    public double totality() { return alignmentMargin() >= 0 ? 1 : 0; }
}
