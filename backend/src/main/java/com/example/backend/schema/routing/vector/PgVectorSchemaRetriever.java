package com.example.backend.schema.routing.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import com.example.backend.schema.routing.model.SchemaIdentity;

import org.springframework.stereotype.Component;

/** Exact cosine retrieval over current approved embeddings in PostgreSQL. */
@Component
public final class PgVectorSchemaRetriever implements SchemaVectorRetriever {
    private final PgVectorSchemaEmbeddingStore store;

    public PgVectorSchemaRetriever(PgVectorSchemaEmbeddingStore store) {
        this.store = store;
    }

    @Override
    public List<SchemaVectorRetriever.RankedVector> rank(EmbeddingResult query, String provider, String model,
            int dimension, int topK, Collection<SchemaIdentity> eligibleSchemaVersions) {
        List<PgVectorSchemaEmbeddingStore.VectorMatch> matches = store.nearest(query, provider, model, dimension,
                topK, eligibleSchemaVersions);
        List<SchemaVectorRetriever.RankedVector> result = new ArrayList<>(matches.size());
        for (int index = 0; index < matches.size(); index++) {
            var match = matches.get(index);
            if (!Double.isFinite(match.similarity()) || match.similarity() < -1.000001 || match.similarity() > 1.000001) {
                throw new IllegalStateException("pgvector returned invalid cosine similarity for schema "
                        + match.schemaId() + "@" + match.schemaVersion());
            }
            result.add(new SchemaVectorRetriever.RankedVector(match.schemaId(), match.schemaVersion(), match.topic(),
                    Math.clamp(match.similarity(), -1.0, 1.0), index + 1));
        }
        return List.copyOf(result);
    }
}
