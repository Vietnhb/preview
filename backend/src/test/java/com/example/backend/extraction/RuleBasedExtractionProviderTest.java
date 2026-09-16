package com.example.backend.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class RuleBasedExtractionProviderTest {
    private final RuleBasedExtractionProvider provider = new RuleBasedExtractionProvider(
            new UnitNormalizer(new ObjectMapper()));

    @Test
    void extractsSupportedProblemAndLeavesMissingRequiredValuesAmbiguous() {
        ProviderExtractionResult result = provider.extract(
                "Một vật chuyển động với vận tốc 10 m/s tại vị trí 0 m.");

        assertThat(result.document().quantities()).extracting(PhysicalQuantity::name)
                .containsExactly("initial_velocity", "initial_position");
        assertThat(result.document().ambiguities()).extracting(AmbiguityItem::fieldPath)
                .containsExactly("quantities.acceleration");
    }

    @Test
    void parsesDimensionlessFrictionAndKeepsDirectionExplicit() {
        PhysicalQuantity friction = provider.explicitQuantity("friction_coefficient", "0,2");
        PhysicalQuantity oppositeVelocity = provider.explicitQuantity("velocity_2", "5 m/s, ngược chiều");

        assertThat(friction.normalizedUnit()).isEqualTo("1");
        assertThat(friction.normalizedValue()).isEqualByComparingTo(new BigDecimal("0.2"));
        assertThat(oppositeVelocity.normalizedValue()).isEqualByComparingTo(new BigDecimal("-5"));
    }

    @Test
    void doesNotGuessAQuantityFromAnUnqualifiedAnswer() {
        assertThatThrownBy(() -> provider.explicitQuantity("mass", "vật nặng"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric value and unit");
    }
}
