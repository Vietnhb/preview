package com.example.backend.physics.compatibility.legacy.model.practical;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical inputs for a bounded linear experimental-data graph. */
public record ExperimentalGraphParameters(double xStart, double xEnd, double slope,
                                          double intercept, int sampleCount) {
    public static ExperimentalGraphParameters from(JsonNode specification, Map<String, Double> overrides) {
        double xStart = PhysicsValues.require(specification, overrides, "x_start");
        double xEnd = PhysicsValues.require(specification, overrides, "x_end");
        double slope = PhysicsValues.require(specification, overrides, "slope");
        double intercept = PhysicsValues.require(specification, overrides, "intercept");
        int sampleCount = (int) Math.round(PhysicsValues.optional(specification, overrides, 25, "sample_count"));
        if (!Double.isFinite(xStart) || !Double.isFinite(xEnd) || !Double.isFinite(slope)
                || !Double.isFinite(intercept) || xEnd <= xStart || sampleCount < 2 || sampleCount > 4096) {
            throw new IllegalArgumentException("Experimental graph bounds, fit and sample count are invalid");
        }
        return new ExperimentalGraphParameters(xStart, xEnd, slope, intercept, sampleCount);
    }

    public double xAt(int index) {
        return xStart + (xEnd - xStart) * index / (sampleCount - 1d);
    }

    public double yAt(int index) {
        return intercept + slope * xAt(index);
    }
}
