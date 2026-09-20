package com.example.backend.exception;

import com.example.backend.dto.common.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTypedMappingsTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsSchemaCompilationToInternalServerError() {
        assertMapping(HttpStatus.INTERNAL_SERVER_ERROR,
                handler.handleSchemaCompilation(new SchemaCompilationException("schemaId=fixture is invalid")));
    }

    @Test
    void mapsUnroutableProblemToUnprocessableEntity() {
        assertMapping(HttpStatus.UNPROCESSABLE_ENTITY,
                handler.handleSchemaRouting(new SchemaRoutingException("No eligible candidates")));
    }

    @Test
    void mapsSolverBindingDefectToInternalServerError() {
        assertMapping(HttpStatus.INTERNAL_SERVER_ERROR,
                handler.handleSolverBinding(new SolverBindingException("schemaId=fixture has no binding")));
    }

    @Test
    void mapsOutOfDomainPhysicsInputToUnprocessableEntity() {
        assertMapping(HttpStatus.UNPROCESSABLE_ENTITY,
                handler.handlePhysicsDomain(new PhysicsDomainException("mass must be positive")));
    }

    @Test
    void mapsInvalidSolverOutputToInternalServerError() {
        assertMapping(HttpStatus.INTERNAL_SERVER_ERROR,
                handler.handleOutputContract(new OutputContractException("output position is missing")));
    }

    private void assertMapping(HttpStatus expected, ResponseEntity<ErrorResponse> response) {
        assertEquals(expected, response.getStatusCode());
        assertEquals(expected.value(), response.getBody().getStatus());
    }
}
