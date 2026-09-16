package com.example.backend.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.backend.entity.ExtractionOutcome;
import com.example.backend.entity.ExtractionPath;

class ExtractionCoordinatorTest {

    private OpenRouterExtractionProvider openRouter;
    private ExtractionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        openRouter = mock(OpenRouterExtractionProvider.class);
        coordinator = new ExtractionCoordinator(openRouter);
    }

    @Test
    void usesOpenRouterWhenProviderSucceeds() {
        SpecificationDocument document = new SpecificationDocument(
                "1.0", "KINEMATICS", "kinematics_1d",
                List.of(), List.of(), List.of(), BigDecimal.ONE, List.of());
        when(openRouter.isAvailable()).thenReturn(true);
        when(openRouter.providerName()).thenReturn("openrouter");
        when(openRouter.modelVersion()).thenReturn("test-model");
        when(openRouter.extract("problem")).thenReturn(new ProviderExtractionResult(document, null));

        ExtractionResult result = coordinator.extract("problem");

        assertThat(result.path()).isEqualTo(ExtractionPath.OPENROUTER);
        assertThat(result.outcome()).isEqualTo(ExtractionOutcome.API_SUCCESS);
        assertThat(result.document()).isSameAs(document);
    }

    @Test
    void usesDeterministicFallbackWhenAiIsNotConfigured() {
        when(openRouter.isAvailable()).thenReturn(false);

        ExtractionResult result = coordinator.extract("Một vật chuyển động với vận tốc 10 m/s và gia tốc 2 m/s2 tại vị trí 0 m.");

        assertThat(result.path()).isEqualTo(ExtractionPath.RULE_BASED);
        assertThat(result.outcome()).isEqualTo(ExtractionOutcome.RULE_BASED_FALLBACK);
        assertThat(result.document().schemaId()).isEqualTo("kinematics_1d");
    }

    @Test
    void propagatesAiFailureInsteadOfUsingHardcodedFallback() {
        when(openRouter.isAvailable()).thenReturn(true);
        when(openRouter.extract("problem")).thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> coordinator.extract("problem"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider unavailable");
    }
}
