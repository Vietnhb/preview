package com.example.backend.extraction;

import java.math.BigDecimal;
import java.util.List;

public record SpecificationDocument(
        String schemaVersion,
        String topic,
        String schemaId,
        List<PhysicalObject> objects,
        List<PhysicalQuantity> quantities,
        List<PhysicalRelation> relations,
        BigDecimal confidence,
        List<AmbiguityItem> ambiguities) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public SpecificationDocument {
        objects = objects == null ? List.of() : List.copyOf(objects);
        quantities = quantities == null ? List.of() : List.copyOf(quantities);
        relations = relations == null ? List.of() : List.copyOf(relations);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
    }
}
