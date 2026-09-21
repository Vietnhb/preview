package com.example.backend.schema.routing.vector;

import java.util.List;
import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.model.SemanticSearchView;

/** JDBC boundary for pgvector. Vector syntax is confined to this infrastructure class. */
@Repository
public class PgVectorSchemaEmbeddingStore {
    private final JdbcTemplate jdbc;

    public PgVectorSchemaEmbeddingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasCurrentEmbedding(SchemaSearchDocument document, String provider, String model, int dimension) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM schema_search_embeddings
                 WHERE schema_id = ? AND schema_version = ? AND embedding_provider = ? AND embedding_model = ?
                   AND embedding_dimension = ? AND source_checksum = ? AND search_text = ?
                   AND projection_version = ?
                """, Integer.class, document.schemaId(), document.schemaVersion(), provider, model, dimension,
                document.sourceChecksum(), document.searchText(), document.projectionVersion());
        return count != null && count > 0;
    }

    public boolean vectorExtensionInstalled() {
        Boolean installed = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')", Boolean.class);
        return Boolean.TRUE.equals(installed);
    }

    public void upsert(SchemaSearchDocument document, String provider, String model, int dimension,
            EmbeddingResult embedding) {
        requireDimension(embedding, dimension);
        String vector = vectorLiteral(embedding.values());
        jdbc.update("""
                INSERT INTO schema_search_embeddings
                    (schema_id, schema_version, topic, search_text, embedding, embedding_provider,
                    embedding_model, embedding_dimension, projection_version, source_checksum, created_at, updated_at)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (schema_id, schema_version, embedding_provider, embedding_model)
                DO UPDATE SET topic = EXCLUDED.topic, search_text = EXCLUDED.search_text,
                    embedding = EXCLUDED.embedding, embedding_dimension = EXCLUDED.embedding_dimension,
                    projection_version = EXCLUDED.projection_version,
                    source_checksum = EXCLUDED.source_checksum, updated_at = CURRENT_TIMESTAMP
                """, document.schemaId(), document.schemaVersion(), document.topic(), document.searchText(), vector,
                provider, model, dimension, document.projectionVersion(), document.sourceChecksum());
    }

    /** Starts an isolated generation. It is not queryable until activateGeneration succeeds. */
    public String startGeneration(String projectionVersion, String provider, String model, int dimension) {
        String generationId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO schema_search_index_generations
                    (generation_id, projection_version, embedding_provider, embedding_model,
                     embedding_dimension, lifecycle_state, created_at)
                VALUES (CAST(? AS uuid), ?, ?, ?, ?, 'BUILDING', CURRENT_TIMESTAMP)
                """, generationId, projectionVersion, provider, model, dimension);
        return generationId;
    }

    public void upsertSemanticView(String generationId, SchemaSearchDocument document,
            SemanticSearchView view, String provider, String model, int dimension, EmbeddingResult embedding) {
        requireDimension(embedding, dimension);
        if (generationId == null || generationId.isBlank()) throw new IllegalArgumentException("Generation is required");
        String vector = vectorLiteral(embedding.values());
        jdbc.update("""
                INSERT INTO schema_search_embedding_views
                    (generation_id, schema_id, schema_version, topic, locale, semantic_view_type,
                     semantic_view_version, semantic_text, embedding, embedding_provider, embedding_model,
                     embedding_dimension, projection_version, source_checksum, lifecycle_state,
                     created_at, updated_at)
                VALUES (CAST(? AS uuid), ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, ?, 'APPROVED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (generation_id, schema_id, schema_version, locale, semantic_view_type,
                             semantic_view_version, embedding_provider, embedding_model)
                DO UPDATE SET topic = EXCLUDED.topic, semantic_text = EXCLUDED.semantic_text,
                    embedding = EXCLUDED.embedding, embedding_dimension = EXCLUDED.embedding_dimension,
                    projection_version = EXCLUDED.projection_version,
                    source_checksum = EXCLUDED.source_checksum, lifecycle_state = 'APPROVED',
                    updated_at = CURRENT_TIMESTAMP
                """, generationId, document.schemaId(), document.schemaVersion(), document.topic(), view.locale(),
                view.viewType(), view.viewVersion(), view.text(), vector, provider, model, dimension,
                document.projectionVersion(), document.metadataChecksum());
    }

    /** Performs the usable-generation swap only after the new generation is complete. */
    @Transactional
    public void activateGeneration(String generationId, String projectionVersion, String provider,
            String model, int dimension) {
        jdbc.update("""
                UPDATE schema_search_index_generations
                   SET lifecycle_state = 'RETIRED'
                 WHERE projection_version = ? AND embedding_provider = ? AND embedding_model = ?
                   AND embedding_dimension = ? AND lifecycle_state = 'ACTIVE'
                """, projectionVersion, provider, model, dimension);
        int updated = jdbc.update("""
                UPDATE schema_search_index_generations
                   SET lifecycle_state = 'ACTIVE', activated_at = CURRENT_TIMESTAMP
                 WHERE generation_id = CAST(? AS uuid) AND projection_version = ?
                   AND embedding_provider = ? AND embedding_model = ? AND embedding_dimension = ?
                   AND lifecycle_state = 'BUILDING'
                """, generationId, projectionVersion, provider, model, dimension);
        if (updated != 1) throw new IllegalStateException("Semantic retrieval generation was not ready for activation");
    }

    public List<VectorMatch> nearest(EmbeddingResult query, String provider, String model, int dimension, int topK,
            Collection<SchemaIdentity> eligibleSchemaVersions) {
        requireDimension(query, dimension);
        if (topK < 1 || topK > 500) throw new IllegalArgumentException("Vector topK must be in [1, 500]");
        if (eligibleSchemaVersions == null) throw new IllegalArgumentException("Eligible schema versions are required");
        if (eligibleSchemaVersions.isEmpty()) return List.of();
        List<SchemaIdentity> orderedIdentities = eligibleSchemaVersions.stream().distinct()
                .sorted(Comparator.comparing(SchemaIdentity::schemaId).thenComparing(SchemaIdentity::schemaVersion))
                .toList();
        List<VectorMatch> semanticMatches = nearestSemanticViews(query, provider, model, dimension, topK,
                orderedIdentities);
        List<VectorMatch> lexicalMatches = nearestLegacyEmbeddings(query, provider, model, dimension, topK,
                orderedIdentities);
        Map<SchemaIdentity, VectorMatch> bestBySchema = new LinkedHashMap<>();
        for (VectorMatch match : lexicalMatches) bestBySchema.put(new SchemaIdentity(match.schemaId(), match.schemaVersion()), match);
        for (VectorMatch match : semanticMatches) {
            SchemaIdentity identity = new SchemaIdentity(match.schemaId(), match.schemaVersion());
            VectorMatch existing = bestBySchema.get(identity);
            if (existing == null || match.similarity() > existing.similarity()) bestBySchema.put(identity, match);
        }
        return bestBySchema.values().stream()
                .sorted(Comparator.comparingDouble(VectorMatch::similarity).reversed()
                        .thenComparing(VectorMatch::schemaId).thenComparing(VectorMatch::schemaVersion))
                .limit(topK).toList();
    }

    private List<VectorMatch> nearestLegacyEmbeddings(EmbeddingResult query, String provider, String model,
            int dimension, int topK, List<SchemaIdentity> orderedIdentities) {
        String eligibleValues = orderedIdentities.stream().map(ignored -> "(?, ?)")
                .collect(Collectors.joining(", "));
        String vector = vectorLiteral(query.values());
        String sql = """
                SELECT e.schema_id, e.schema_version, e.topic,
                       1.0 - (e.embedding <=> CAST(? AS vector)) AS similarity
                  FROM schema_search_embeddings e
                  JOIN schema_versions s ON s.schema_id = e.schema_id AND s.version = e.schema_version
                  JOIN topics t ON LOWER(t.name) = LOWER(e.topic) AND t.enabled = TRUE
                 WHERE e.embedding_provider = ? AND e.embedding_model = ? AND e.embedding_dimension = ?
                   AND e.projection_version = ?
                   AND s.lifecycle_status = 'APPROVED'
                   AND s.definition_checksum IS NOT NULL AND s.definition_checksum = e.source_checksum
                   AND (e.schema_id, e.schema_version) IN (VALUES %s)
                 ORDER BY e.embedding <=> CAST(? AS vector), e.schema_id ASC, e.schema_version ASC
                 LIMIT ?
                """.formatted(eligibleValues);
        List<Object> arguments = new ArrayList<>();
        arguments.add(vector);
        arguments.add(provider);
        arguments.add(model);
        arguments.add(dimension);
        arguments.add(SchemaSearchDocument.CURRENT_PROJECTION_VERSION);
        for (SchemaIdentity identity : orderedIdentities) {
            arguments.add(identity.schemaId());
            arguments.add(identity.schemaVersion());
        }
        arguments.add(vector);
        arguments.add(topK);
        return jdbc.query(sql, (rs, row) -> new VectorMatch(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getDouble(4)), arguments.toArray());
    }

    private List<VectorMatch> nearestSemanticViews(EmbeddingResult query, String provider, String model,
            int dimension, int topK, List<SchemaIdentity> orderedIdentities) {
        if (!tableExists("schema_search_embedding_views")) return List.of();
        String eligibleValues = orderedIdentities.stream().map(ignored -> "(?, ?)")
                .collect(Collectors.joining(", "));
        String sql = """
                SELECT v.schema_id, v.schema_version, v.topic,
                       MAX(1.0 - (v.embedding <=> CAST(? AS vector))) AS similarity
                  FROM schema_search_embedding_views v
                  JOIN schema_search_index_generations g ON g.generation_id = v.generation_id
                  JOIN schema_versions s ON s.schema_id = v.schema_id AND s.version = v.schema_version
                  JOIN topics t ON LOWER(t.name) = LOWER(v.topic) AND t.enabled = TRUE
                 WHERE g.lifecycle_state = 'ACTIVE'
                   AND g.projection_version = ? AND g.embedding_provider = ? AND g.embedding_model = ?
                   AND g.embedding_dimension = ?
                   AND v.embedding_provider = ? AND v.embedding_model = ? AND v.embedding_dimension = ?
                   AND v.projection_version = ?
                   AND s.lifecycle_status = 'APPROVED' AND v.lifecycle_state = 'APPROVED'
                   AND (v.schema_id, v.schema_version) IN (VALUES %s)
                 GROUP BY v.schema_id, v.schema_version, v.topic
                 ORDER BY similarity DESC, v.schema_id ASC, v.schema_version ASC
                 LIMIT ?
                """.formatted(eligibleValues);
        List<Object> arguments = new ArrayList<>();
        arguments.add(vectorLiteral(query.values()));
        arguments.add(SchemaSearchDocument.MULTILINGUAL_PROJECTION_VERSION);
        arguments.add(provider);
        arguments.add(model);
        arguments.add(dimension);
        arguments.add(provider);
        arguments.add(model);
        arguments.add(dimension);
        arguments.add(SchemaSearchDocument.MULTILINGUAL_PROJECTION_VERSION);
        for (SchemaIdentity identity : orderedIdentities) {
            arguments.add(identity.schemaId());
            arguments.add(identity.schemaVersion());
        }
        arguments.add(topK);
        return jdbc.query(sql, (rs, row) -> new VectorMatch(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getDouble(4)), arguments.toArray());
    }

    private boolean tableExists(String table) {
        try {
            Boolean exists = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table);
            return Boolean.TRUE.equals(exists);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public record VectorMatch(String schemaId, String schemaVersion, String topic, double similarity) { }

    private void requireDimension(EmbeddingResult vector, int expected) {
        if (vector == null || vector.values().size() != expected) {
            throw new IllegalArgumentException("Embedding dimension mismatch: expected=" + expected + ", actual="
                    + (vector == null ? "null" : vector.values().size()));
        }
        for (Double value : vector.values()) {
            if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("Embedding must be finite");
        }
    }

    private String vectorLiteral(List<Double> values) {
        return values.stream().map(value -> Double.toString(value)).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

}
