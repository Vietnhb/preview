package com.example.backend.schema.routing.model;

import java.util.List;
import java.util.Objects;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;

/** One approved, version-pinned candidate with its JEV signals. */
public record SchemaCandidate(
        CandidateContractProjection contract,
        List<String> evidenceCodes,
        double confidence) {
    public SchemaCandidate {
        Objects.requireNonNull(contract, "contract");
        evidenceCodes = List.copyOf(evidenceCodes);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Candidate confidence must be in [0, 1]");
        }
    }

    public String schemaId() { return contract.schemaId(); }
    public String schemaVersion() { return contract.schemaVersion(); }
    public String topic() { return contract.topic(); }

}
