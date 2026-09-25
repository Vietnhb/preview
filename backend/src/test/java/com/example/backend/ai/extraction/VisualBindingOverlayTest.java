package com.example.backend.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class VisualBindingOverlayTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void overlaysBindingsWithoutChangingConfirmedPhysics() throws Exception {
        var current = mapper.readTree("""
                {
                  "schemaId":"constant-acceleration",
                  "schemaVersion":"1.0",
                  "objects":[{"id":"body-1","label":"Vật","type":"body","quantities":[]}],
                  "quantities":[{"name":"initial_velocity","symbol":"v0","value":10,"originalUnit":"m/s"}],
                  "relations":[],
                  "endCondition":{"type":"time_limit","duration":8},
                  "ambiguities":[],
                  "visualBindings":[]
                }
                """);
        var bindings = mapper.readTree("""
                {"visualBindings":[{"targetId":"actor-1","entityId":"body-1","assetId":"cart-blue",
                  "match":"EXACT","visualDifference":null}]}
                """);

        var merged = StructuredExtractionProvider.overlayVisualBindings(mapper, current, bindings);

        for (String field : new String[] {"schemaId", "schemaVersion", "objects", "quantities", "relations", "endCondition"}) {
            assertEquals(current.path(field), merged.path(field), field + " must remain unchanged");
        }
        assertEquals(bindings.path("visualBindings"), merged.path("visualBindings"));
    }

    @Test
    void rejectsAnyPhysicsFieldsInBindingResponse() throws Exception {
        var current = mapper.readTree("{\"schemaId\":\"s\"}");
        var response = mapper.readTree("{\"schemaId\":\"other\",\"visualBindings\":[]}");

        assertThrows(IllegalArgumentException.class,
                () -> StructuredExtractionProvider.overlayVisualBindings(mapper, current, response));
    }

}
