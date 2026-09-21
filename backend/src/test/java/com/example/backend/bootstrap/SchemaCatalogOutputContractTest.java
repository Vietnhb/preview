package com.example.backend.bootstrap;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.service.problem.SchemaCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ensures every active schema has a complete catalog-derived typed output contract. */
class SchemaCatalogOutputContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyLatestActiveIdentityCompilesWithTypedOutputDefinitions() throws Exception {
        SchemaCompiler compiler = new SchemaCompiler(objectMapper, new UnitNormalizer(objectMapper));
        Map<String, JsonNode> latest = new HashMap<>();
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            for (JsonNode entry : objectMapper.readTree(input)) {
                latest.merge(entry.path("schemaId").asText(), entry,
                        (left, right) -> compareVersions(left.path("version").asText(), right.path("version").asText()) >= 0
                                ? left : right);
            }
        }

        assertEquals(74, latest.size(), "the active catalog identity count must be explicit in the gate");
        for (JsonNode entry : latest.values()) {
            String schemaId = entry.path("schemaId").asText();
            String version = entry.path("version").asText();
            ObjectNode definition = entry.path("definition").deepCopy();
            definition.put("model", entry.path("model").asText());
            JsonNode outputDefinitions = definition.path("output").path("definitions");
            assertNotNull(outputDefinitions);
            assertFalse(outputDefinitions.isMissingNode() || outputDefinitions.isEmpty(),
                    schemaId + "@" + version + " must declare output definitions");
            var compiled = compiler.compile(definition, schemaId, version, entry.path("topic").asText());
            assertEquals(outputDefinitions.size(), compiled.outputDefinitions().size(),
                    schemaId + "@" + version + " output definition coverage");
        }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int size = Math.max(a.length, b.length);
        for (int i = 0; i < size; i++) {
            int av = i < a.length ? Integer.parseInt(a[i]) : 0;
            int bv = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }
}
