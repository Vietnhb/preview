package com.example.backend.physics.model;


import java.util.ArrayList;
import java.util.List;

/** Shared bounded grid construction for wave-family solvers. */
public final class WaveFieldGrid {
    private WaveFieldGrid() { }

    public static int timeSamples(double duration, double step) {
        positiveFinite(duration, "durationSeconds");
        positiveFinite(step, "stepSeconds");
        double intervals = Math.ceil(duration / step);
        if (!Double.isFinite(intervals) || intervals > ScalarField.MAX_TIME_SAMPLES - 1L) {
            throw new IllegalArgumentException("Time sampling exceeds scalar-field resource limits");
        }
        return (int) intervals + 1;
    }

    public static List<Double> timeCoordinates(double duration, double step, int samples) {
        List<Double> result = new ArrayList<>(samples);
        for (int index = 0; index < samples; index++) result.add(Math.min(duration, index * step));
        return result;
    }

    public static List<Double> spaceCoordinates(double start, double end, int samples) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || end <= start || samples < 2) {
            throw new IllegalArgumentException("Wave domain must be finite, increasing and sampled at least twice");
        }
        double step = (end - start) / (samples - 1d);
        List<Double> result = new ArrayList<>(samples);
        for (int index = 0; index < samples; index++) {
            result.add(index == samples - 1 ? end : start + index * step);
        }
        return result;
    }

    public static double positiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(label + " must be finite and positive");
        return value;
    }
}
