package com.example.backend.schema.routing.vector;

import java.util.List;
import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.schema.routing.model.SchemaIdentity;

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
                """, Integer.class, document.schemaId(), document.schemaVersion(), provider, model, dimension,
                document.sourceChecksum(), document.searchText());
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
                     embedding_model, embedding_dimension, source_checksum, created_at, updated_at)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (schema_id, schema_version, embedding_provider, embedding_model)
                DO UPDATE SET topic = EXCLUDED.topic, search_text = EXCLUDED.search_text,
                    embedding = EXCLUDED.embedding, embedding_dimension = EXCLUDED.embedding_dimension,
                    source_checksum = EXCLUDED.source_checksum, updated_at = CURRENT_TIMESTAMP
                """, document.schemaId(), document.schemaVersion(), document.topic(), document.searchText(), vector,
                provider, model, dimension, document.sourceChecksum());
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
        for (SchemaIdentity identity : orderedIdentities) {
            arguments.add(identity.schemaId());
            arguments.add(identity.schemaVersion());
        }
        arguments.add(vector);
        arguments.add(topK);
        return jdbc.query(sql, (rs, row) -> new VectorMatch(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getDouble(4)), arguments.toArray());
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
