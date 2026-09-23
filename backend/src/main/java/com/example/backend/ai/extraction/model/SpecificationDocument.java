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
        List<AmbiguityItem> ambiguities,
        String contractVersion,
        List<com.example.backend.simulation.assets.VisualBinding> visualBindings) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public SpecificationDocument {
        objects = objects == null ? List.of() : List.copyOf(objects);
        quantities = quantities == null ? List.of() : List.copyOf(quantities);
        relations = relations == null ? List.of() : List.copyOf(relations);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
        visualBindings = visualBindings == null ? List.of() : List.copyOf(visualBindings);
    }

    public SpecificationDocument withAmbiguities(List<AmbiguityItem> additional) {
        var merged = new java.util.ArrayList<>(ambiguities);
        if (additional != null) {
            for (AmbiguityItem item : additional) {
                if (item == null || merged.stream().anyMatch(existing ->
                        java.util.Objects.equals(existing.fieldPath(), item.fieldPath()))) continue;
                merged.add(item);
            }
        }
        return new SpecificationDocument(schemaVersion, topic, schemaId, objects, quantities, relations,
                endCondition, confidence, merged, contractVersion, visualBindings);
    }

    public SpecificationDocument withoutAmbiguitiesAt(java.util.Set<String> fieldPaths) {
        if (fieldPaths == null || fieldPaths.isEmpty()) return this;
        var retained = ambiguities.stream().filter(item -> !fieldPaths.contains(item.fieldPath())).toList();
        return new SpecificationDocument(schemaVersion, topic, schemaId, objects, quantities, relations,
                endCondition, confidence, retained, contractVersion, visualBindings);
    }

    public SpecificationDocument withoutAmbiguitiesMatching(
            java.util.function.Predicate<AmbiguityItem> predicate) {
        if (predicate == null) return this;
        var retained = ambiguities.stream().filter(item -> !predicate.test(item)).toList();
        return new SpecificationDocument(schemaVersion, topic, schemaId, objects, quantities, relations,
                endCondition, confidence, retained, contractVersion, visualBindings);
    }

    public SpecificationDocument(String schemaVersion, String topic, String schemaId,
            List<PhysicalObject> objects, List<PhysicalQuantity> quantities, List<PhysicalRelation> relations,
            com.fasterxml.jackson.databind.JsonNode endCondition, BigDecimal confidence,
            List<AmbiguityItem> ambiguities, String contractVersion) {
        this(schemaVersion, topic, schemaId, objects, quantities, relations, endCondition, confidence,
                ambiguities, contractVersion, List.of());
    }

    /** Source compatibility for integrations/tests that still build v1 documents. */
    public SpecificationDocument(String schemaVersion, String topic, String schemaId,
            List<PhysicalObject> objects, List<PhysicalQuantity> quantities,
            List<PhysicalRelation> relations, BigDecimal confidence,
            List<AmbiguityItem> ambiguities) {
        this(null, topic, schemaId, objects, quantities, relations, null, confidence, ambiguities,
                CURRENT_SCHEMA_VERSION);
    }

    /** Source-compatible overload for callers that already provide an end condition. */
    public SpecificationDocument(String schemaVersion, String topic, String schemaId,
            List<PhysicalObject> objects, List<PhysicalQuantity> quantities,
            List<PhysicalRelation> relations, com.fasterxml.jackson.databind.JsonNode endCondition,
            BigDecimal confidence, List<AmbiguityItem> ambiguities) {
        this(schemaVersion, topic, schemaId, objects, quantities, relations, endCondition, confidence,
                ambiguities, CURRENT_SCHEMA_VERSION);
    }
}
