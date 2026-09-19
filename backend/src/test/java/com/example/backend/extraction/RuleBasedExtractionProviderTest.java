package com.example.backend.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.backend.entity.SchemaVersion;
import com.example.backend.service.SchemaDefinitionService;
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

    @Test
    void usesGroundContactOnlyForSchemasWithAVerticalPositionSeries() {
        ProviderExtractionResult result = provider.extract(
                "An object moves at velocity 10 m/s from position 0 m until it reaches the ground.");

        assertThat(result.document().schemaId()).isEqualTo("kinematics_1d");
        assertThat(result.document().endCondition().path("type").asText()).isEqualTo("time_limit");
    }

    @Test
    void extractsExplicitGravityAsAnOptionalSchemaQuantity() {
        ProviderExtractionResult result = provider.extract(
                "A projectile is launched at 20 m/s from 5 m at angle 1 rad with gravity g=3 m/s2.");

        assertThat(result.document().quantities()).anySatisfy(quantity -> {
            assertThat(quantity.name()).isEqualTo("gravitational_acceleration");
            assertThat(quantity.normalizedValue()).isEqualByComparingTo(new BigDecimal("3"));
            assertThat(quantity.normalizedUnit()).isEqualTo("m/s2");
        });
    }

    @Test
    void usesTheApprovedSchemaDurationForFallbackEndCondition() throws Exception {
        SchemaVersion schema = new SchemaVersion();
        schema.setDefinition(new ObjectMapper().readTree("""
                {"execution":{"durationSeconds":3.5}}
                """));
        SchemaDefinitionService definitions = Mockito.mock(SchemaDefinitionService.class);
        Mockito.when(definitions.requireApproved("kinematics_1d")).thenReturn(schema);
        RuleBasedExtractionProvider configuredProvider = new RuleBasedExtractionProvider(
                new UnitNormalizer(new ObjectMapper()), definitions);

        ProviderExtractionResult result = configuredProvider.extract(
                "A body moves at 10 m/s from position 0 m with acceleration 2 m/s2.");

        assertThat(result.document().endCondition().path("duration").asDouble()).isEqualTo(3.5);
    }
}
