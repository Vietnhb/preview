package com.example.backend.ai.extraction.model;

import java.math.BigDecimal;
import java.util.List;

public record SpecificationDocument(
        String schemaVersion,
        String topic,
        String schemaId,
        List<PhysicalObject> objects,
        List<PhysicalQuantity> quantities,
        List<PhysicalRelation> relations,
        com.fasterxml.jackson.databind.JsonNode endCondition,
        BigDecimal confidence,
        List<AmbiguityItem> ambiguities) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public SpecificationDocument {
        objects = objects == null ? List.of() : List.copyOf(objects);
        quantities = quantities == null ? List.of() : List.copyOf(quantities);
        relations = relations == null ? List.of() : List.copyOf(relations);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
    }

    /** Source compatibility for integrations/tests that still build v1 documents. */
    public SpecificationDocument(String schemaVersion, String topic, String schemaId,
            List<PhysicalObject> objects, List<PhysicalQuantity> quantities,
            List<PhysicalRelation> relations, BigDecimal confidence,
            List<AmbiguityItem> ambiguities) {
        this(schemaVersion, topic, schemaId, objects, quantities, relations, null, confidence, ambiguities);
    }
}
