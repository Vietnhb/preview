package com.example.backend.physics.model.modern;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical inputs for exponential radioactive decay. */
public record RadioactiveDecayParameters(double initialCount, double decayConstant) {
    public static RadioactiveDecayParameters from(JsonNode specification, Map<String, Double> overrides) {
        double initial = PhysicsValues.require(specification, overrides, "initial_count");
        double decay = PhysicsValues.require(specification, overrides, "decay_constant");
        if (initial < 0 || decay < 0) throw new IllegalArgumentException("Initial count and decay constant must be non-negative");
        return new RadioactiveDecayParameters(initial, decay);
    }
}
