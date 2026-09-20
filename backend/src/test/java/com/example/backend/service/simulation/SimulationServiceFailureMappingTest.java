package com.example.backend.service.simulation;

import com.example.backend.exception.ApiException;
import com.example.backend.exception.CanonicalContractException;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.example.backend.exception.OutputContractException;
import com.example.backend.exception.PhysicsDomainException;
import com.example.backend.exception.SchemaCompilationException;
import com.example.backend.exception.SchemaRoutingException;
import com.example.backend.exception.SolverBindingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SimulationServiceFailureMappingTest {
    @Test
    void preservesTypedClientAndServerFailuresInsteadOfFlatteningThemToUnprocessableEntity() {
        List<RuntimeException> typedFailures = List.of(
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "embedding unavailable"),
                new CanonicalContractException("canonical quantity rejected"),
                new EmbeddingUnavailableException("reindex the schema catalog"),
                new OutputContractException("required output is missing"),
                new PhysicsDomainException("mass must be positive"),
                new SchemaCompilationException("schema version is invalid"),
                new SchemaRoutingException("no eligible candidates"),
                new SolverBindingException("solver binding is missing"));

        for (RuntimeException failure : typedFailures) {
            assertSame(failure, SimulationService.translateSimulationFailure(failure));
        }
    }

    @Test
    void keepsExistingUnclassifiedSimulationFailureMapping() {
        RuntimeException failure = new RuntimeException("legacy solver rejected request");

        RuntimeException translated = SimulationService.translateSimulationFailure(failure);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ((ApiException) translated).getStatus());
        assertEquals("Simulation failed: legacy solver rejected request", translated.getMessage());
    }
}
