package com.example.backend.physics;

import com.example.backend.physics.compatibility.LegacySpecificationAdapter;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsValuesCanonicalTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsCanonicalQuantityNamesAndOverridesOnly() {
        ObjectNode specification = mapper.createObjectNode();
        ArrayNode quantities = specification.putArray("quantities");
        quantities.addObject().put("name", "initial_velocity").put("normalizedValue", 12.5);

        assertEquals(12.5, PhysicsValues.require(specification, Map.of(), "initial_velocity"));
        assertEquals(8.0, PhysicsValues.require(specification, Map.of("initial_velocity", 8.0), "initial_velocity"));
    }

    @Test
    void doesNotGuessPresentationAliasesInsideTheSolverBoundary() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("speed", 12.5);
        specification.putArray("quantities").addObject().put("name", "v").put("normalizedValue", 12.5);

        assertThrows(IllegalArgumentException.class,
                () -> PhysicsValues.require(specification, Map.of(), "initial_velocity"));
    }

    @Test
    void doesNotStripDimensionalSuffixesFromRuntimeIdentifiers() {
        ObjectNode specification = mapper.createObjectNode().put("schemaId", "motion_2d").put("model", "motion-1d");

        assertEquals("motion_2d", PhysicsValues.schema(specification));
        assertEquals("motion-1d", PhysicsValues.model(specification));
    }

    @Test
    void canonicalBagRejectsDuplicateKeysAndKeepsLegacyAtAdapterBoundary() {
        ObjectNode duplicate = mapper.createObjectNode();
        duplicate.putArray("quantities")
                .addObject().put("name", "mass").put("normalizedValue", 1).put("normalizedUnit", "kg");
        duplicate.withArray("quantities").addObject().put("name", "mass")
                .put("normalizedValue", 2).put("normalizedUnit", "kg");
        assertThrows(IllegalArgumentException.class, () -> PhysicsValues.bag(duplicate, Map.of()));

        ObjectNode legacy = mapper.createObjectNode().put("mass", 2.0);
        assertEquals(2.0, PhysicsValues.require(legacy, Map.of(), "mass"));
    }

    @Test
    void legacySpecificationAdaptationIsReachedThroughTheDeprecatedPhysicsValuesBridge() {
        long before = LegacySpecificationAdapter.invocationCount();
        ObjectNode legacy = mapper.createObjectNode().put("mass", 2.0);

        assertEquals(2.0, PhysicsValues.bag(legacy, Map.of()).require("mass"));
        assertTrue(LegacySpecificationAdapter.invocationCount() > before);
    }
}
