package com.example.backend.physics.model;

import com.example.backend.physics.model.ScalarField;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Version 1 of the transport-safe scalar-field payload shared by numerical
 * solvers, persistence and the renderer. Values are indexed as
 * {@code values[timeIndex][spaceIndex]} for a single spatial axis and
 * {@code values[timeIndex][xIndex * yCount + yIndex]} when a second axis is
 * present. The flattened row keeps the transport contract bounded and
 * backwards-compatible while the axes carry the physical coordinates.
 */
public record ScalarField(
        int version,
        String type,
        int physicalDimension,
        List<Axis> axes,
        List<Integer> shape,
        List<Double> time,
        List<List<Double>> values,
        String valueUnit,
        String timeUnit,
        Sampling sampling,
        String interpolation,
        String boundary) {

    public static final int CONTRACT_VERSION = 1;
    public static final String TYPE = "scalarField";
    public static final int MAX_SPATIAL_SAMPLES = 4_096;
    public static final int MAX_TIME_SAMPLES = 16_384;
    public static final int MAX_CELLS = 1_000_000;
    private static final double EPSILON = 1e-9;

    public ScalarField {
        if (version != CONTRACT_VERSION) {
            throw new IllegalArgumentException("Unsupported scalar-field contract version: " + version);
        }
        if (!TYPE.equals(type)) {
            throw new IllegalArgumentException("Scalar field type must be " + TYPE);
        }
        if (physicalDimension != 1 && physicalDimension != 2) {
            throw new IllegalArgumentException("Scalar field physicalDimension must be 1 or 2");
        }
        if (axes == null || axes.size() != physicalDimension) {
            throw new IllegalArgumentException("Scalar field axes must match its physical dimension");
        }
        axes = List.copyOf(axes);
        Axis xAxis = axes.getFirst();
        if (!"x".equals(xAxis.key()) || !"m".equals(xAxis.unit())) {
            throw new IllegalArgumentException("Scalar fields require an x axis in metres");
        }
        Axis yAxis = null;
        if (physicalDimension == 2) {
            yAxis = axes.get(1);
            if (!"y".equals(yAxis.key()) || !"m".equals(yAxis.unit())) {
                throw new IllegalArgumentException("Plane fields require a y axis in metres");
            }
        }
        if (valueUnit == null || valueUnit.isBlank()) {
            throw new IllegalArgumentException("Scalar field valueUnit is required");
        }
        if (!"s".equals(timeUnit)) {
            throw new IllegalArgumentException("Scalar field timeUnit must be seconds");
        }
        if (!"linear".equals(interpolation)) {
            throw new IllegalArgumentException("Scalar field interpolation must be linear");
        }
        if (boundary == null || boundary.isBlank()) {
            throw new IllegalArgumentException("Scalar field boundary is required");
        }
        int expectedShapeLength = physicalDimension + 1;
        if (shape == null || shape.size() != expectedShapeLength || shape.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Scalar field shape must be [time, x] or [time, x, y]");
        }
        shape = List.copyOf(shape);
        if (time == null || values == null || sampling == null) {
            throw new IllegalArgumentException("Scalar field time, values and sampling are required");
        }
        time = copyFiniteSeries(time, "time");
        List<Double> xCoordinates = xAxis.coordinates();
        List<Double> yCoordinates = yAxis == null ? List.of() : yAxis.coordinates();
        if (xCoordinates.size() < 2 || (physicalDimension == 2 && yCoordinates.size() < 2) || time.size() < 2) {
            throw new IllegalArgumentException("Scalar field needs at least two samples on every axis");
        }
        int spatialCells = xCoordinates.size() * (physicalDimension == 2 ? yCoordinates.size() : 1);
        if (shape.get(0) != time.size() || shape.get(1) != xCoordinates.size()
                || (physicalDimension == 2 && shape.get(2) != yCoordinates.size())) {
            throw new IllegalArgumentException("Scalar field shape does not match time and spatial coordinates");
        }
        if (physicalDimension == 2) validateResourceShape(time.size(), xCoordinates.size(), yCoordinates.size());
        else validateResourceShape(time.size(), xCoordinates.size());
        assertStrictlyIncreasing(xCoordinates, "x coordinates");
        if (physicalDimension == 2) assertStrictlyIncreasing(yCoordinates, "y coordinates");
        assertStrictlyIncreasing(time, "time");
        if (time.getFirst() < 0) {
            throw new IllegalArgumentException("Scalar field time must be non-negative");
        }
        validateSampling(xCoordinates, yCoordinates, time, sampling, physicalDimension);
        values = copyAndValidateValues(values, time.size(), spatialCells);
    }

    /** The spatial domain is derived from the authoritative x coordinates. */
    @JsonIgnore
    public Domain domain() {
        Axis xAxis = axes.getFirst();
        return new Domain(xAxis.coordinates().getFirst(), xAxis.coordinates().getLast(), xAxis.unit());
    }

    /** The y-domain is present only when the optional second axis is enabled. */
    @JsonIgnore
    public Domain domainY() {
        if (physicalDimension != 2) return null;
        List<Double> coordinates = axes.get(1).coordinates();
        return new Domain(coordinates.getFirst(), coordinates.getLast(), axes.get(1).unit());
    }

    public static void validateResourceShape(int timeSamples, int spatialSamples) {
        if (timeSamples < 2 || spatialSamples < 2) {
            throw new IllegalArgumentException("Scalar field needs at least two samples on each axis");
        }
        if (timeSamples > MAX_TIME_SAMPLES || spatialSamples > MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("Scalar field sampling exceeds resource limits");
        }
        long cells = (long) timeSamples * spatialSamples;
        if (cells > MAX_CELLS) {
            throw new IllegalArgumentException("Scalar field cell count exceeds resource limits");
        }
    }

    /** Resource guard for a rectangular spatial grid with a second axis. */
    public static void validateResourceShape(int timeSamples, int xSamples, int ySamples) {
        if (timeSamples < 2 || xSamples < 2 || ySamples < 2) {
            throw new IllegalArgumentException("Scalar field needs at least two samples on each axis");
        }
        if (timeSamples > MAX_TIME_SAMPLES || xSamples > MAX_SPATIAL_SAMPLES || ySamples > MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("Scalar field sampling exceeds resource limits");
        }
        long cells = (long) timeSamples * xSamples * ySamples;
        if (cells > MAX_CELLS) {
            throw new IllegalArgumentException("Scalar field cell count exceeds resource limits");
        }
    }

    private static List<Double> copyFiniteSeries(List<Double> input, String label) {
        List<Double> copy = new ArrayList<>(input.size());
        for (Double value : input) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Scalar field " + label + " must contain only finite values");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static void assertStrictlyIncreasing(List<Double> values, String label) {
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index) <= values.get(index - 1)) {
                throw new IllegalArgumentException("Scalar field " + label + " must be strictly increasing");
            }
        }
    }

    private static void validateSampling(List<Double> xCoordinates, List<Double> yCoordinates,
                                         List<Double> time, Sampling sampling, int physicalDimension) {
        double expectedSpaceStep = xCoordinates.get(1) - xCoordinates.get(0);
        if (!approximatelyEqual(expectedSpaceStep, sampling.spaceStep()) || sampling.spaceStep() <= 0) {
            throw new IllegalArgumentException("Scalar field spaceStep does not match x coordinates");
        }
        for (int index = 2; index < xCoordinates.size(); index++) {
            if (!approximatelyEqual(xCoordinates.get(index) - xCoordinates.get(index - 1), sampling.spaceStep())) {
                throw new IllegalArgumentException("Scalar field x coordinates must be uniformly sampled");
            }
        }
        if (physicalDimension == 2) {
            for (int index = 1; index < yCoordinates.size(); index++) {
                if (!approximatelyEqual(yCoordinates.get(index) - yCoordinates.get(index - 1), sampling.spaceStep())) {
                    throw new IllegalArgumentException("Scalar field y coordinates must use the same uniform spaceStep");
                }
            }
        }
        if (sampling.timeStep() <= 0) {
            throw new IllegalArgumentException("Scalar field timeStep must be positive");
        }
        for (int index = 1; index < time.size(); index++) {
            double interval = time.get(index) - time.get(index - 1);
            if (interval > sampling.timeStep() + EPSILON) {
                throw new IllegalArgumentException("Scalar field time interval exceeds sampling timeStep");
            }
            if (index < time.size() - 1 && !approximatelyEqual(interval, sampling.timeStep())) {
                throw new IllegalArgumentException("Only the final scalar-field time interval may be shortened");
            }
        }
    }

    private static List<List<Double>> copyAndValidateValues(List<List<Double>> input, int timeSamples,
                                                              int spatialSamples) {
        if (input.size() != timeSamples) {
            throw new IllegalArgumentException("Scalar field values do not match the time dimension");
        }
        List<List<Double>> copy = new ArrayList<>(input.size());
        for (List<Double> row : input) {
            if (row == null || row.size() != spatialSamples) {
                throw new IllegalArgumentException("Scalar field values do not match the spatial dimension");
            }
            copy.add(copyFiniteSeries(row, "values"));
        }
        return List.copyOf(copy);
    }

    private static boolean approximatelyEqual(double left, double right) {
        return Math.abs(left - right) <= EPSILON * Math.max(1, Math.max(Math.abs(left), Math.abs(right)));
    }

    public record Axis(String key, String unit, List<Double> coordinates) {
        public Axis {
            if (key == null || key.isBlank() || unit == null || unit.isBlank() || coordinates == null) {
                throw new IllegalArgumentException("Scalar field axis key, unit and coordinates are required");
            }
            coordinates = copyFiniteSeries(coordinates, "coordinates");
        }
    }

    public record Sampling(double spaceStep, double timeStep) {
        public Sampling {
            if (!Double.isFinite(spaceStep) || !Double.isFinite(timeStep)) {
                throw new IllegalArgumentException("Scalar field sampling values must be finite");
            }
        }
    }

    public record Domain(double start, double end, String unit) {
        public Domain {
            if (!Double.isFinite(start) || !Double.isFinite(end) || end <= start || unit == null || unit.isBlank()) {
                throw new IllegalArgumentException("Scalar field domain is invalid");
            }
        }
    }
}
