package com.example.backend.schema.routing.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Authoritative localized retrieval metadata kept separate from the physics contract. */
public record SchemaRetrievalMetadata(
        String schemaId,
        String schemaVersion,
        String topic,
        Map<String, String> localizedNames,
        Map<String, List<String>> aliases,
        Map<String, List<String>> curriculumLabels,
        List<SemanticSearchView> semanticViews,
        String metadataChecksum) {

    public SchemaRetrievalMetadata {
        schemaId = required(schemaId, "schemaId");
        schemaVersion = required(schemaVersion, "schemaVersion");
        topic = required(topic, "topic");
        localizedNames = immutableStrings(localizedNames);
        aliases = immutableLists(aliases);
        curriculumLabels = immutableLists(curriculumLabels);
        semanticViews = List.copyOf(Objects.requireNonNull(semanticViews, "semanticViews"));
        metadataChecksum = required(metadataChecksum, "metadataChecksum");
        if (semanticViews.size() > 4) {
            throw new IllegalArgumentException("A schema may have at most four semantic views");
        }
        if (!localizedNames.containsKey("en") || !localizedNames.containsKey("vi")) {
            throw new IllegalArgumentException("Every localized retrieval metadata entry needs en and vi names");
        }
        if (semanticViews.stream().noneMatch(view -> view.locale().equals("en"))
                || semanticViews.stream().noneMatch(view -> view.locale().equals("vi"))) {
            throw new IllegalArgumentException("Every localized retrieval metadata entry needs en and vi views");
        }
    }

    private static Map<String, String> immutableStrings(Map<String, String> values) {
        TreeMap<String, String> result = new TreeMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && value != null && !key.isBlank() && !value.isBlank()) {
                    result.put(key.trim(), value.trim());
                }
            });
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, List<String>> immutableLists(Map<String, List<String>> values) {
        TreeMap<String, List<String>> result = new TreeMap<>();
        if (values != null) {
            values.forEach((key, list) -> {
                if (key == null || key.isBlank() || list == null) return;
                List<String> cleaned = list.stream().filter(Objects::nonNull).map(String::trim)
                        .filter(value -> !value.isBlank()).distinct().sorted().toList();
                if (!cleaned.isEmpty()) result.put(key.trim(), cleaned);
            });
        }
        return Collections.unmodifiableMap(result);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
