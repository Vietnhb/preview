package com.example.backend.schema.routing.model;

import java.util.List;
import java.util.Objects;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;

/** One version-pinned candidate with retrieval and contract-verification evidence. */
public record SchemaCandidate(
        CandidateContractProjection contract,
        SchemaSelectionScore selectionScore,
        VerificationEvidence verificationEvidence,
        double confidence) {
    public SchemaCandidate {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(selectionScore, "selectionScore");
        Objects.requireNonNull(verificationEvidence, "verificationEvidence");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Candidate confidence must be in [0, 1]");
        }
    }

    public String schemaId() { return contract.schemaId(); }
    public String schemaVersion() { return contract.schemaVersion(); }
    public String topic() { return contract.topic(); }

    public record VerificationEvidence(
            double requiredQuantityCoverage,
            double unitCompatibility,
            double metadataOverlap,
            double contractContradiction,
            List<String> evidenceCodes) {
        public VerificationEvidence(double requiredQuantityCoverage, double unitCompatibility, double metadataOverlap,
                List<String> evidenceCodes) {
            this(requiredQuantityCoverage, unitCompatibility, metadataOverlap, 0, evidenceCodes);
        }

        public VerificationEvidence {
            evidenceCodes = List.copyOf(evidenceCodes);
            for (double score : List.of(requiredQuantityCoverage, unitCompatibility, metadataOverlap,
                    contractContradiction)) {
                if (!Double.isFinite(score) || score < 0 || score > 1) {
                    throw new IllegalArgumentException("Verification evidence scores must be in [0, 1]");
                }
            }
        }
    }
}
