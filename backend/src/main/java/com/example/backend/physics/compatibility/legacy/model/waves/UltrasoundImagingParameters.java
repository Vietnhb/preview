package com.example.backend.physics.compatibility.legacy.model.waves;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Pulse-echo ultrasound depth and wavelength relationships. */
public record UltrasoundImagingParameters(double soundSpeed, double frequency, double echoTime) {
    public static UltrasoundImagingParameters from(JsonNode specification, Map<String, Double> overrides) {
        double speed = PhysicsValues.require(specification, overrides, "sound_speed");
        double frequency = PhysicsValues.require(specification, overrides, "frequency");
        double echo = PhysicsValues.require(specification, overrides, "echo_time");
        if (!(speed > 0 && frequency > 0) || echo < 0) throw new IllegalArgumentException("Invalid ultrasound inputs");
        return new UltrasoundImagingParameters(speed, frequency, echo);
    }
    public double wavelength() { return soundSpeed / frequency; }
    public double depth() { return soundSpeed * echoTime / 2.0; }
    public double period() { return 1 / frequency; }
}
