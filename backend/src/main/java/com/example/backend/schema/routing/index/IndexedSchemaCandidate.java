package com.example.backend.schema.routing.index;

import java.util.Objects;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.schema.routing.model.SchemaSearchDocument;

/** Search document plus the compact extraction contract for one pinned version. */
public record IndexedSchemaCandidate(
        SchemaSearchDocument document,
        CandidateContractProjection contract) {
    public IndexedSchemaCandidate {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(contract, "contract");
        if (!document.schemaId().equals(contract.schemaId())
                || !document.schemaVersion().equals(contract.schemaVersion())) {
            throw new IllegalArgumentException("Search document and contract identities must match");
        }
    }
}
