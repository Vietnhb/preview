package com.example.backend.schema.routing.model;

import java.util.Objects;

/** Stable, version-pinned identity shared by the approved search snapshot and retrievers. */
public record SchemaIdentity(String schemaId, String schemaVersion) {
    public SchemaIdentity {
        schemaId = requireText(schemaId, "schemaId");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
