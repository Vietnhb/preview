package com.example.backend.physics;

import com.example.backend.physics.compatibility.LegacyScalarOutputSeriesAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyScalarOutputSeriesAdapterTest {
    @Test
    void projectsScalarsOnlyAtTheLegacyResponseBoundary() {
        Map<String, List<Double>> projected = LegacyScalarOutputSeriesAdapter.project(
                Map.of("voltage", List.of(1.0, 2.0)), Map.of("doseRate", 3.0), 3);

        assertEquals(List.of(1.0, 2.0), projected.get("voltage"));
        assertEquals(List.of(3.0, 3.0, 3.0), projected.get("doseRate"));
        assertThrows(UnsupportedOperationException.class, () -> projected.get("doseRate").add(4.0));
        assertThrows(UnsupportedOperationException.class, () -> projected.put("other", List.of(1.0)));
    }

    @Test
    void rejectsDuplicateOrInvalidScalarProjection() {
        assertThrows(IllegalArgumentException.class, () -> LegacyScalarOutputSeriesAdapter.project(
                Map.of("doseRate", List.of(1.0)), Map.of("doseRate", 1.0), 1));
        assertThrows(IllegalArgumentException.class, () -> LegacyScalarOutputSeriesAdapter.project(
                Map.of(), Map.of("doseRate", Double.NaN), 1));
    }
}
