package com.example.backend.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UnitNormalizerTest {

    private final UnitNormalizer normalizer = new UnitNormalizer(new ObjectMapper());

    @Test
    void convertsKilometresPerHourToMetresPerSecond() {
        UnitNormalizer.NormalizedQuantity result = normalizer.normalize(new BigDecimal("36"), "km/h");

        assertThat(result.normalizedValue()).isEqualByComparingTo("10");
        assertThat(result.normalizedUnit()).isEqualTo("m/s");
        assertThat(result.knownUnit()).isTrue();
    }

    @Test
    void convertsGramsToKilograms() {
        UnitNormalizer.NormalizedQuantity result = normalizer.normalize(new BigDecimal("500"), "g");

        assertThat(result.normalizedValue()).isEqualByComparingTo("0.5");
        assertThat(result.normalizedUnit()).isEqualTo("kg");
    }

    @Test
    void preservesUnknownUnitsForHumanConfirmation() {
        UnitNormalizer.NormalizedQuantity result = normalizer.normalize(new BigDecimal("2"), "custom-unit");

        assertThat(result.normalizedValue()).isEqualByComparingTo("2");
        assertThat(result.normalizedUnit()).isEqualTo("custom-unit");
        assertThat(result.knownUnit()).isFalse();
    }
}
