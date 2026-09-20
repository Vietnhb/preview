package com.example.backend.physics.model;

/** Small, reusable domain checks for parameter binders. */
public final class PhysicalChecks {
    private PhysicalChecks() {
    }

    public static double finite(double value, String key) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }

    public static double positive(double value, String key) {
        finite(value, key);
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    public static double nonNegative(double value, String key) {
        finite(value, key);
        if (value < 0) throw new IllegalArgumentException(key + " must be non-negative");
        return value;
    }

    public static double integer(double value, String key) {
        finite(value, key);
        if (Math.rint(value) != value) throw new IllegalArgumentException(key + " must be an integer");
        return value;
    }

    public static double range(double value, double min, double max, String key) {
        finite(value, key);
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }
}
