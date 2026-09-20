package com.example.backend.service.problem;

import com.example.backend.entity.problem.SchemaVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SchemaVersionOrderingTest {
    @Test
    void samePublicationTimestampUsesNumericVersionOrderDeterministically() {
        Instant publishedAt = Instant.parse("2026-01-01T00:00:00Z");
        SchemaVersion older = schema("1.9", publishedAt);
        SchemaVersion newer = schema("1.10", publishedAt);

        assertSame(newer, SchemaVersionOrdering.newer(older, newer));
        assertSame(newer, SchemaVersionOrdering.newer(newer, older));
        assertEquals(1, SchemaVersionOrdering.compareVersions("1.10", "1.9"));
    }

    @Test
    void newerSemanticVersionWinsOverCreationTimestamp() {
        SchemaVersion numericallyHigher = schema("2.0", Instant.parse("2026-01-01T00:00:00Z"));
        SchemaVersion laterPublished = schema("1.9", Instant.parse("2026-01-02T00:00:00Z"));

        assertSame(numericallyHigher, SchemaVersionOrdering.newer(numericallyHigher, laterPublished));
    }

    private SchemaVersion schema(String version, Instant createdAt) {
        SchemaVersion schema = new SchemaVersion();
        schema.setVersion(version);
        schema.setCreatedAt(createdAt);
        return schema;
    }
}
