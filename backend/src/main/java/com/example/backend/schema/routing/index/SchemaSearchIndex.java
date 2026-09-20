package com.example.backend.schema.routing.index;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.backend.schema.routing.lexical.Bm25SchemaRetriever;
import com.example.backend.schema.routing.model.SchemaIdentity;

/** Immutable, atomically swapped approved-schema retrieval snapshot. */
@Component
public final class SchemaSearchIndex {
    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(List.of(), Map.of(), null));

    public Snapshot snapshot() {
        Snapshot snapshot = current.get();
        if (!snapshot.ready()) throw new IllegalStateException("Schema search index is not ready");
        return snapshot;
    }

    public void replace(Collection<IndexedSchemaCandidate> candidates, Bm25SchemaRetriever.Index bm25Index) {
        if (candidates == null) throw new IllegalArgumentException("Schema candidates are required");
        if (bm25Index == null) throw new IllegalArgumentException("Prebuilt BM25 index is required");
        List<IndexedSchemaCandidate> ordered = candidates.stream()
                .sorted(Comparator.comparing((IndexedSchemaCandidate item) -> item.document().schemaId())
                        .thenComparing(item -> item.document().schemaVersion()))
                .toList();
        Map<SchemaIdentity, IndexedSchemaCandidate> byIdentity = ordered.stream().collect(Collectors.toUnmodifiableMap(
                item -> new SchemaIdentity(item.document().schemaId(), item.document().schemaVersion()), item -> item));
        if (ordered.isEmpty()) throw new IllegalStateException("No enabled approved schema documents are available for routing");
        current.set(new Snapshot(ordered, byIdentity, bm25Index));
    }

    /** Drops any possibly stale snapshot after a failed lifecycle refresh. */
    public void invalidate() {
        current.set(new Snapshot(List.of(), Map.of(), null));
    }

    public record Snapshot(List<IndexedSchemaCandidate> candidates, Map<SchemaIdentity, IndexedSchemaCandidate> byIdentity,
            Bm25SchemaRetriever.Index bm25Index) {
        public Snapshot {
            candidates = List.copyOf(candidates);
            byIdentity = Map.copyOf(byIdentity);
        }
        public boolean ready() { return !candidates.isEmpty() && bm25Index != null; }
    }
}
