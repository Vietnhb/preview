package com.example.backend.physics.compatibility.legacy.model.modern;

import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Hydrogen emission/absorption line from the Rydberg relation. */
public record HydrogenSpectrumParameters(double initialLevel, double finalLevel) {
    public static final double RYDBERG = 10_973_731.568160;
    public static final double PLANCK = PhysicalConstants.PLANCK;
    public static final double SPEED_OF_LIGHT = PhysicalConstants.SPEED_OF_LIGHT;
    public static HydrogenSpectrumParameters from(JsonNode specification, Map<String, Double> overrides) {
        double initial = PhysicsValues.require(specification, overrides, "initial_level");
        double finish = PhysicsValues.require(specification, overrides, "final_level");
        if (!(initial > finish) || !(finish >= 1) || initial != Math.rint(initial) || finish != Math.rint(finish))
            throw new IllegalArgumentException("Hydrogen levels must be integers with initial_level > final_level >= 1");
        return new HydrogenSpectrumParameters(initial, finish);
    }
    public double inverseWavelength() { return RYDBERG * (1 / (finalLevel * finalLevel) - 1 / (initialLevel * initialLevel)); }
    public double wavelength() { return 1 / inverseWavelength(); }
    public double frequency() { return SPEED_OF_LIGHT / wavelength(); }
    public double photonEnergy() { return PLANCK * frequency(); }
}
