package com.example.backend.service.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaQuantityCanonicalizationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaDefinitionService schemas = new SchemaDefinitionService(null, null, null);

    @Test
    void mapsSchemaAliasesOnceAtTheSpecificationBoundary() {
        ObjectNode definition = mapper.createObjectNode();
        ArrayNode required = definition.putArray("requiredQuantities");
        required.addObject().put("key", "initial_velocity")
                .putArray("aliases").add("v0").add("velocity").add("speed").add("v");

        ArrayNode quantities = mapper.createArrayNode();
        quantities.addObject().put("name", "speed").put("normalizedValue", 4);
        var canonical = schemas.canonicalizeQuantities(quantities, definition);

        assertEquals("initial_velocity", canonical.get(0).path("name").asText());
        assertEquals(4, canonical.get(0).path("normalizedValue").asDouble());
    }

    @Test
    void validatesIntegerAndSameUnitConstraintsAtSchemaBoundary() {
        ObjectNode definition = mapper.createObjectNode();
        ArrayNode required = definition.putArray("requiredQuantities");
        required.addObject().put("key", "turns").put("integer", true).putArray("allowedUnits").add("1");
        required.addObject().put("key", "measured_value").putArray("allowedUnits").add("m");
        required.addObject().put("key", "absolute_uncertainty").put("sameUnitAs", "measured_value")
                .putArray("allowedUnits").add("m");
        definition.putObject("execution").put("durationSeconds", 1);

        ArrayNode quantities = mapper.createArrayNode();
        quantities.addObject().put("name", "turns").put("normalizedValue", 2.5).put("normalizedUnit", "1");
        quantities.addObject().put("name", "measured_value").put("normalizedValue", 1).put("normalizedUnit", "m");
        quantities.addObject().put("name", "absolute_uncertainty").put("normalizedValue", .1).put("normalizedUnit", "s");
        var blockers = schemas.validateSpecification(mapper.createObjectNode().set("quantities", quantities), definition);

        assertTrue(blockers.stream().anyMatch(item -> item.contains("turns")));
        assertTrue(blockers.stream().anyMatch(item -> item.contains("absolute_uncertainty") && item.contains("measured_value")));
    }
}
