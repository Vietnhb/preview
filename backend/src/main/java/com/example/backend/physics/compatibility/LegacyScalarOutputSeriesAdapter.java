package com.example.backend.physics.compatibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned API compatibility projection for clients that only understand
 * timeline series. Core solver output and persisted snapshots retain scalars
 * as one value; repetition happens only while creating the legacy response.
 */
public final class LegacyScalarOutputSeriesAdapter {
    private LegacyScalarOutputSeriesAdapter() { }

    public static Map<String, List<Double>> project(Map<String, List<Double>> series,
            Map<String, Double> scalarOutputs, int timelineSamples) {
        if (timelineSamples < 0) throw new IllegalArgumentException("Timeline sample count cannot be negative");
        Map<String, List<Double>> result = new LinkedHashMap<>();
        if (series != null) {
            series.forEach((key, values) -> {
                if (key == null || key.isBlank() || values == null) {
                    throw new IllegalArgumentException("Legacy output series must have a key and values");
                }
                result.put(key, List.copyOf(values));
            });
        }
        if (scalarOutputs != null) {
            scalarOutputs.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("Legacy scalar projection requires a key and finite value");
                }
                if (result.containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate typed and legacy output key: " + key);
                }
                result.put(key, Collections.unmodifiableList(new ArrayList<>(
                        Collections.nCopies(timelineSamples, value))));
            });
        }
        return Collections.unmodifiableMap(result);
    }
}
