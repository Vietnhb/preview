package com.example.backend.schema.routing.index;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.example.backend.schema.routing.model.SchemaRetrievalMetadata;
import com.example.backend.schema.routing.model.SemanticSearchView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Loads and validates the versioned, source-controlled multilingual metadata projection. */
@Component
public final class SchemaRetrievalMetadataCatalog {
    static final String RESOURCE = "schemas/retrieval/multilingual-metadata-v2.json";
    static final String PROJECTION_VERSION = "v2";
    static final String METADATA_VERSION = "2026-09-21";

    private final Map<String, SchemaRetrievalMetadata> entries;

    public SchemaRetrievalMetadataCatalog(ObjectMapper objectMapper) {
        this.entries = load(objectMapper);
    }

    public Optional<SchemaRetrievalMetadata> find(String schemaId, String schemaVersion) {
        return Optional.ofNullable(entries.get(key(schemaId, schemaVersion)));
    }

    public int size() {
        return entries.size();
    }

    public boolean isMvpTopic(String topic) {
        return entries.values().stream().anyMatch(entry -> entry.topic().equalsIgnoreCase(topic));
    }

    private Map<String, SchemaRetrievalMetadata> load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            if (!PROJECTION_VERSION.equals(root.path("projectionVersion").asText())) {
                throw new IllegalStateException("Unsupported retrieval metadata projection version");
            }
            if (!METADATA_VERSION.equals(root.path("metadataVersion").asText())) {
                throw new IllegalStateException("Unsupported retrieval metadata version");
            }
            JsonNode schemas = root.path("schemas");
            if (!schemas.isArray() || schemas.isEmpty()) {
                throw new IllegalStateException("Retrieval metadata must contain schema entries");
            }
            Map<String, SchemaRetrievalMetadata> result = new HashMap<>();
            for (JsonNode node : schemas) {
                SchemaRetrievalMetadata metadata = parse(node);
                if (result.putIfAbsent(key(metadata.schemaId(), metadata.schemaVersion()), metadata) != null) {
                    throw new IllegalStateException("Duplicate retrieval metadata identity: "
                            + metadata.schemaId() + "@" + metadata.schemaVersion());
                }
            }
            return Map.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load multilingual schema retrieval metadata", exception);
        }
    }

    private SchemaRetrievalMetadata parse(JsonNode node) {
        String schemaId = text(node, "schemaId");
        String version = text(node, "schemaVersion");
        String topic = text(node, "topic");
        Map<String, String> names = objectStrings(node.path("localizedNames"));
        Map<String, List<String>> aliases = objectLists(node.path("aliases"));
        Map<String, List<String>> labels = objectLists(node.path("curriculumLabels"));
        List<SemanticSearchView> views = new ArrayList<>();
        for (JsonNode view : node.path("semanticViews")) {
            views.add(new SemanticSearchView(text(view, "locale"), text(view, "viewType"),
                    text(view, "viewVersion"), text(view, "text")));
        }
        String checksum = checksum(schemaId, version, topic, names, aliases, labels, views);
        return new SchemaRetrievalMetadata(schemaId, version, topic, names, aliases, labels, views, checksum);
    }

    private Map<String, String> objectStrings(JsonNode node) {
        Map<String, String> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                throw new IllegalStateException("Localized metadata values must be non-empty text");
            }
            result.put(entry.getKey(), entry.getValue().asText());
        });
        return result;
    }

    private Map<String, List<String>> objectLists(JsonNode node) {
        Map<String, List<String>> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isArray()) throw new IllegalStateException("Metadata aliases/labels must be arrays");
            List<String> values = new ArrayList<>();
            for (JsonNode value : entry.getValue()) {
                if (!value.isTextual() || value.asText().isBlank()) {
                    throw new IllegalStateException("Metadata aliases/labels must be non-empty text");
                }
                values.add(value.asText());
            }
            result.put(entry.getKey(), values);
        });
        return result;
    }

    private String checksum(String schemaId, String version, String topic, Map<String, String> names,
            Map<String, List<String>> aliases, Map<String, List<String>> labels, List<SemanticSearchView> views) {
        StringBuilder canonical = new StringBuilder(METADATA_VERSION).append('\n')
                .append(schemaId).append('\n').append(version).append('\n').append(topic);
        names.keySet().stream().sorted().forEach(locale -> canonical.append("\nname|").append(locale).append('|').append(names.get(locale)));
        appendLists(canonical, "alias", aliases);
        appendLists(canonical, "label", labels);
        views.stream().sorted(java.util.Comparator.comparing(SemanticSearchView::locale)
                .thenComparing(SemanticSearchView::viewType).thenComparing(SemanticSearchView::viewVersion)
                .thenComparing(SemanticSearchView::text))
                .forEach(view -> canonical.append("\nview|").append(view.locale()).append('|')
                        .append(view.viewType()).append('|').append(view.viewVersion()).append('|').append(view.text()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot checksum retrieval metadata", exception);
        }
    }

    private void appendLists(StringBuilder canonical, String kind, Map<String, List<String>> values) {
        values.keySet().stream().sorted().forEach(locale -> values.get(locale).stream().sorted()
                .forEach(value -> canonical.append('\n').append(kind).append('|').append(locale).append('|').append(value)));
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw new IllegalStateException("Retrieval metadata field is missing: " + field);
        return value;
    }

    private static String key(String schemaId, String version) {
        return schemaId + "@" + version;
    }
}
