package com.example.backend.schema.routing.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable, version-pinned retrieval projection of approved schema metadata. */
public record SchemaSearchDocument(
        String schemaId,
        String schemaVersion,
        String topic,
        String name,
        String modelId,
        String description,
        Set<String> learningOutcomes,
        Set<String> canonicalQuantityKeys,
        Set<String> aliases,
        Set<String> symbols,
        Set<String> allowedUnits,
        Set<String> relationTypes,
        Set<String> endConditionCapabilities,
        Set<String> curriculumLabels,
        String searchText,
        String projectionVersion,
        String sourceChecksum,
        List<SemanticSearchView> semanticViews,
        String metadataChecksum) {

    /** Bump when the metadata projection or tokenizer contract changes. */
    public static final String CURRENT_PROJECTION_VERSION = "v1";
    public static final String MULTILINGUAL_PROJECTION_VERSION = "v2";

    /** The canonical lexical view; accent folding is retrieval-only and never replaces this text. */
    public String lexicalText() {
        return searchText;
    }

    public String accentFoldedLexicalText() {
        return com.example.backend.schema.routing.lexical.UnicodePhysicsNormalizer.accentFold(searchText);
    }

    public SchemaSearchDocument {
        schemaId = requireText(schemaId, "schemaId");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        topic = requireText(topic, "topic");
        name = requireText(name, "name");
        modelId = requireText(modelId, "modelId");
        description = optionalText(description);
        learningOutcomes = immutableSorted(learningOutcomes);
        canonicalQuantityKeys = immutableSorted(canonicalQuantityKeys);
        aliases = immutableSorted(aliases);
        symbols = immutableSorted(symbols);
        allowedUnits = immutableSorted(allowedUnits);
        relationTypes = immutableSorted(relationTypes);
        endConditionCapabilities = immutableSorted(endConditionCapabilities);
        curriculumLabels = immutableSorted(curriculumLabels);
        searchText = requireText(searchText, "searchText");
        projectionVersion = requireText(projectionVersion, "projectionVersion");
        sourceChecksum = requireText(sourceChecksum, "sourceChecksum");
        semanticViews = List.copyOf(Objects.requireNonNull(semanticViews, "semanticViews"));
        metadataChecksum = requireText(metadataChecksum, "metadataChecksum");
    }

    /** Source-compatible constructor for retrieval fixtures that only supply text. */
    public SchemaSearchDocument(String schemaId, String schemaVersion, String topic, String name,
            String modelId, String searchText, String sourceChecksum) {
        this(schemaId, schemaVersion, topic, name, modelId, "", Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), searchText,
                CURRENT_PROJECTION_VERSION, sourceChecksum, List.of(), sourceChecksum);
    }

    public SchemaSearchDocument(String schemaId, String schemaVersion, String topic, String name,
            String modelId, String searchText, String projectionVersion, String sourceChecksum) {
        this(schemaId, schemaVersion, topic, name, modelId, "", Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), searchText,
                projectionVersion, sourceChecksum, List.of(), sourceChecksum);
    }

    private static Set<String> immutableSorted(Set<String> values) {
        TreeSet<String> sorted = new TreeSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = optionalText(value);
                if (!normalized.isEmpty()) sorted.add(normalized);
            }
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static String requireText(String value, String field) {
        String normalized = optionalText(value);
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
