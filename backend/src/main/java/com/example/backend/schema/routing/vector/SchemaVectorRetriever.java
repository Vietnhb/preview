package com.example.backend.schema.routing.vector;

import java.util.List;
import java.util.Collection;
import com.example.backend.schema.routing.model.SchemaIdentity;

/** Domain-facing contract for bounded vector retrieval, independent of database types. */
public interface SchemaVectorRetriever {
    List<RankedVector> rank(EmbeddingResult query, String provider, String model, int dimension, int topK,
            Collection<SchemaIdentity> eligibleSchemaVersions);

    record RankedVector(String schemaId, String schemaVersion, String topic, double similarity, int rank) { }
}
