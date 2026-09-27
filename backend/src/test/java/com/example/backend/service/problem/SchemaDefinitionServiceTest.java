package com.example.backend.service.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaDefinitionServiceTest {
    private final SchemaDefinitionService service = new SchemaDefinitionService(null, null, null, new ObjectMapper());

    @Test
    void checksumDoesNotDependOnObjectFieldOrder() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertEquals(service.compiledChecksum(json.readTree("{\"topic\":\"KINEMATICS\",\"version\":\"2.0\"}")),
                service.compiledChecksum(json.readTree("{\"version\":\"2.0\",\"topic\":\"KINEMATICS\"}")));
    }

    @Test
    void topicDefinitionMustBeObjectShapedAndHaveIdentity() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertThrows(RuntimeException.class, () -> service.validateDefinition(
                json.readTree("[]"), "adaptive_kinematics", "2.0", "KINEMATICS"));
        assertThrows(RuntimeException.class, () -> service.validateDefinition(
                json.readTree("{}"), "", "2.0", "KINEMATICS"));
    }
}
