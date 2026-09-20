package com.example.backend.service.problem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaVisualizationContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaDefinitionService schemas = new SchemaDefinitionService(null, null, null);

    @Test
    void everyBootstrapSchemaHasAProductionSafeVisualization() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/schemas/catalog.json")) {
            JsonNode catalog = mapper.readTree(input);
            for (JsonNode schema : catalog) {
                com.fasterxml.jackson.databind.node.ObjectNode definition = schema.path("definition").deepCopy();
                definition.put("model", schema.path("model").asText());
                assertDoesNotThrow(() -> schemas.validateDefinition(definition, schema.path("schemaId").asText()));
            }
        }
    }

    @Test
    void rejectsUnknownSceneCodeAndMalformedVectorGeometry() throws Exception {
        JsonNode invalidPrimitive = validDefinition();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidPrimitive.at("/visualization/presentation/sceneGraph/nodes/0"))
                .put("type", "lessonSpecificMagic");
        assertThrows(ApiException.class, () -> schemas.validateDefinition(invalidPrimitive, "invalid"));

        JsonNode invalidGeometry = validDefinition();
        ((com.fasterxml.jackson.databind.node.ArrayNode) invalidGeometry.at("/visualization/presentation/sceneGraph/nodes/0/properties/vector/viewBox"))
                .set(2, mapper.getNodeFactory().numberNode(0));
        assertThrows(ApiException.class, () -> schemas.validateDefinition(invalidGeometry, "invalid"));

        JsonNode undeclaredBinding = validDefinition();
        ((com.fasterxml.jackson.databind.node.ObjectNode) undeclaredBinding
                .at("/visualization/presentation/sceneGraph/nodes/0/properties/vector/shapes/0"))
                .put("x", "values.notProducedBySolver");
        assertThrows(ApiException.class, () -> schemas.validateDefinition(undeclaredBinding, "invalid"));
    }

    @Test
    void materializesOnlySchemaOwnedOptionalDefaults() throws Exception {
        JsonNode definition = mapper.readTree("""
                {"optionalQuantities":[{"key":"gravitational_acceleration","allowedUnits":["m/s2"],"defaultValue":9.81}]}
                """);
        JsonNode input = mapper.readTree("{\"quantities\":[]}");
        JsonNode materialized = schemas.materializeDefaults(input, definition);
        assertEquals("gravitational_acceleration", materialized.path("quantities").get(0).path("name").asText());
        assertEquals(9.81, materialized.path("quantities").get(0).path("normalizedValue").asDouble());
    }

    private JsonNode validDefinition() throws Exception {
        return mapper.readTree("""
                {"model":"test","requiredQuantities":[],"adjustableParameters":[],
                 "visualization":{"scene":"test","series":[],"presentation":{"sceneGraph":{"nodes":[
                   {"id":"diagram","type":"vectorScene","properties":{"vector":{"viewBox":[0,0,10,10],
                    "shapes":[{"kind":"rect","x":0,"y":0,"width":2,"height":3,"fill":"#fff"}]}}}
                 ]}}},
                 "execution":{"durationSeconds":1,"stepSeconds":0.1,"durationBindings":[]},
                 "validation":{"tolerance":0.000001}}
                """);
    }
}
