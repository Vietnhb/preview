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

    @Test
    void ambiguityResolutionCannotDropUnrelatedConfirmedPhysicsFacts() throws Exception {
        var current = mapper.readTree("""
                {
                  "quantities":[
                    {"name":"initial_velocity","value":10,"originalUnit":"m/s","normalizedValue":10,"normalizedUnit":"m/s"},
                    {"name":"acceleration","value":2,"originalUnit":"m/s^2","normalizedValue":2,"normalizedUnit":"m/s^2"}
                  ],
                  "objects":[{"id":"body-1","quantities":[]}],
                  "relations":[{"type":"same_direction","subject":"body-1","object":"body-2"}],
                  "endCondition":{"type":"time_limit","duration":8},
                  "ambiguities":[{"code":"need-position","fieldPath":"quantities.initial_position"}]
                }
                """);
        var updated = mapper.readTree("""
                {
                  "quantities":[{"name":"initial_position","value":0,"originalUnit":"m"}],
                  "objects":[{"id":"body-1","quantities":[]}],
                  "relations":[],
                  "endCondition":{"type":"time_limit","duration":80},
                  "ambiguities":[
                    {"code":"need-velocity","fieldPath":"quantities.initial_velocity"},
                    {"code":"need-acceleration","fieldPath":"quantities.acceleration"}
                  ]
                }
                """);
        var decisions = List.of(new com.example.backend.ai.extraction.model.ResolutionDecision(
                "need-position", com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ANSWERED,
                List.of()));

        var preserved = StructuredExtractionProvider.preserveConfirmedPhysics(mapper, current, updated, decisions);

        assertEquals(3, preserved.path("quantities").size());
        assertEquals(10, preserved.path("quantities").get(1).path("value").asInt());
        assertEquals(2, preserved.path("quantities").get(2).path("value").asInt());
        assertEquals(current.path("relations"), preserved.path("relations"));
        assertEquals(current.path("endCondition"), preserved.path("endCondition"));
        assertEquals(0, preserved.path("ambiguities").size(),
                "new questions for already confirmed quantities must be removed");
    }
}
