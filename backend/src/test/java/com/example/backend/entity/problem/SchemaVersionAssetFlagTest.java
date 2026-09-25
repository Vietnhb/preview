package com.example.backend.entity.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.backend.simulation.assets.VisualTargets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaVersionAssetFlagTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void apiFlagReflectsActualSvgTargets() throws Exception {
        assertFlag("""
                {"visualization":{"presentation":{"actors":[{"id":"body","x":"positions.x"}]}}}
                """, true);
        assertFlag("""
                {"visualization":{"presentation":{"props":["device"]}}}
                """, true);
        assertFlag("""
                {"visualization":{"presentation":{"sceneGraph":{"nodes":[
                  {"id":"body","type":"body"}
                ]}}}}
                """, true);
        assertFlag("""
                {"visualization":{"presentation":{"sceneGraph":{"nodes":[
                  {"id":"plot","type":"graph"}
                ]}}}}
                """, false);
    }

    @Test
    void apiFlagIsPresentAndCorrectForEveryCatalogSchemaVersion() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/schemas/catalog.json")) {
            assertNotNull(input);
            JsonNode catalog = mapper.readTree(input);
            Set<String> schemaIds = new HashSet<>();
            int withAssets = 0;
            int withoutAssets = 0;

            for (JsonNode entry : catalog) {
                schemaIds.add(entry.path("schemaId").asText());
                SchemaVersion schema = new SchemaVersion();
                schema.setDefinition(entry.path("definition"));
                JsonNode apiJson = mapper.readTree(mapper.writeValueAsString(schema));
                boolean expected = !VisualTargets.read(entry.path("definition").path("visualization")).isEmpty();

                assertTrue(apiJson.has("hasSvgAsset"), entry.path("schemaId").asText());
                assertEquals(expected, apiJson.path("hasSvgAsset").asBoolean(),
                        entry.path("schemaId").asText() + "@" + entry.path("version").asText());
                if (expected) withAssets++;
                else withoutAssets++;
            }

            assertTrue(!schemaIds.isEmpty());
            assertTrue(withAssets > 0);
            assertTrue(withoutAssets > 0);
        }
    }

    private void assertFlag(String definition, boolean expected) throws Exception {
        SchemaVersion schema = new SchemaVersion();
        schema.setDefinition(mapper.readTree(definition));
        var apiJson = mapper.readTree(mapper.writeValueAsString(schema));
        assertEquals(expected, apiJson.path("hasSvgAsset").asBoolean());
    }
}
