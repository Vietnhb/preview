package com.example.backend.physics;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitNormalizerPrecisionTest {
    @Test void preservesModernPhysicsScaleDuringUnitNormalization() {
        UnitNormalizer normalizer = new UnitNormalizer(new ObjectMapper());
        var normalized = normalizer.normalize(new BigDecimal("1e-30"), "kg");
        assertEquals(new BigDecimal("1e-30"), normalized.normalizedValue());
    }

    @Test void recognizesFahrenheitOutputUnitWithoutTreatingItAsDimensionless() {
        UnitNormalizer normalizer = new UnitNormalizer(new ObjectMapper());
        var normalized = normalizer.normalize(new BigDecimal("32"), "°F");
        assertEquals(new BigDecimal("32"), normalized.normalizedValue());
        assertEquals("degF", normalized.normalizedUnit());
        assertTrue(normalized.knownUnit());
    }
}
