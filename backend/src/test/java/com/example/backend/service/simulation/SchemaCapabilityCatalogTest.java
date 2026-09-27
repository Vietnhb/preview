package com.example.backend.service.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Every executable capability in the approved catalog computes and passes its own verification contract. */
class SchemaCapabilityCatalogTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SchemaEquationRuntime runtime = new SchemaEquationRuntime(json, 0.02, 3.2e9, 16384, 256, 32, 512);

    @Test void everyCapabilityHasASampleAndVerifies() throws Exception {
        JsonNode catalog = json.readTree(new ClassPathResource("schemas/catalog.json").getInputStream());
        JsonNode samples = json.readTree(new ClassPathResource("capability-samples.json").getInputStream());
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (JsonNode entry : catalog) for (JsonNode capability : entry.path("definition").path("capabilities")) {
            String id = capability.path("capabilityId").asText();
            assertTrue(samples.has(id), "No verification sample for capability " + id);
            for (JsonNode sample : samples.path(id)) {
                ObjectNode spec = json.createObjectNode();
                spec.put("durationSeconds", sample.path("_duration").asDouble(5));
                spec.put("physicsCoverage", "COMPLETE");
                ArrayNode parameters = spec.putArray("parameters");
                ObjectNode model = spec.putArray("physicsModels").addObject();
                model.put("id", "sample").put("capabilityId", id);
                ObjectNode inputs = model.putObject("inputs");
                for (JsonNode input : capability.path("canonicalInputs")) {
                    String key = input.path("key").asText();
                    if (!sample.has(key)) continue;
                    parameters.addObject().put("name", "p_" + key).put("value", sample.path(key).asDouble())
                            .put("unit", input.path("unit").asText()).put("min", -1e30).put("max", 1e30);
                    inputs.put(key, "p_" + key);
                }
                String status = runtime.compute(entry.path("definition"), spec, json.createObjectNode())
                        .path("validation").path("status").asText();
                if (!status.startsWith("VERIFIED")) failures.add(id + " -> " + status);
                checked++;
            }
        }
        assertTrue(checked >= 40, "Expected the curriculum catalog to expose executable capabilities");
        assertEquals(List.of(), failures);
    }
}
