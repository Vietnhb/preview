package com.example.backend.dto.simulation;

public record ValidationCheckpointResponse(
        double time,
        String quantity,
        double numerical,
        double analytical,
        double absoluteError,
        double relativeError,
        double tolerance,
        double absoluteTolerance,
        double relativeTolerance,
        boolean passed) {

    /** Backward-compatible constructor for clients/tests using the v1 shape. */
    public ValidationCheckpointResponse(double time, String quantity, double numerical, double analytical,
            double relativeError, double tolerance, boolean passed) {
        this(time, quantity, numerical, analytical, Math.abs(numerical - analytical), relativeError,
                tolerance, tolerance, tolerance, passed);
    }
}
