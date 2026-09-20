package com.example.backend.bootstrap;

import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.exception.SolverBindingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Startup/CI guard that requires each current approved catalog identity to resolve through one typed module.
 * Historical, retired catalog entries are intentionally not passed to this audit.
 */
public final class ApprovedLatestPhysicsModuleBindingAudit {
    public record CatalogBinding(String schemaId, String version, String modelId,
                                 String numericalSolverId, String referenceSolverId) {
        public CatalogBinding {
            requireText(schemaId, "schemaId");
            requireText(version, "version");
            requireText(modelId, "modelId");
            requireText(numericalSolverId, "numericalSolverId");
            requireText(referenceSolverId, "referenceSolverId");
        }
    }

    private ApprovedLatestPhysicsModuleBindingAudit() { }

    public static List<CatalogBinding> fromActiveCatalog(JsonNode catalog) {
        if (catalog == null || !catalog.isArray()) {
            throw new IllegalStateException("Active approved schema catalog must be a JSON array");
        }
        List<CatalogBinding> bindings = new java.util.ArrayList<>();
        for (JsonNode entry : catalog) {
            bindings.add(new CatalogBinding(entry.path("schemaId").asText(), entry.path("version").asText(),
                    entry.path("model").asText(), entry.path("solverId").asText(),
                    entry.path("referenceSolverId").asText()));
        }
        return List.copyOf(bindings);
    }

    /** Audits only the latest version for each schema ID in the active approved source catalog. */
    public static void requireTypedBindings(Collection<CatalogBinding> catalog,
                                            PhysicsModuleRegistry registry) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(registry, "registry");

        Map<String, CatalogBinding> latestBySchema = new HashMap<>();
        Set<String> identities = new HashSet<>();
        for (CatalogBinding binding : catalog) {
            Objects.requireNonNull(binding, "catalog binding");
            String identity = binding.schemaId() + "@" + binding.version();
            if (!identities.add(identity)) {
                throw new IllegalStateException("Duplicate active schema catalog identity: " + identity);
            }
            CatalogBinding previous = latestBySchema.get(binding.schemaId());
            if (previous == null || compareVersions(binding.version(), previous.version()) > 0) {
                latestBySchema.put(binding.schemaId(), binding);
            }
        }
        if (latestBySchema.isEmpty()) {
            throw new IllegalStateException("Active approved schema catalog is empty; typed module bindings cannot be audited");
        }

        List<CatalogBinding> latest = latestBySchema.values().stream()
                .sorted(Comparator.comparing(CatalogBinding::schemaId))
                .toList();
        for (CatalogBinding binding : latest) {
            try {
                if (!registry.supportsPair(binding.numericalSolverId(), binding.referenceSolverId())) {
                    throw missingBinding(binding);
                }
            } catch (SolverBindingException mismatch) {
                throw new IllegalStateException("Approved latest schema " + binding.schemaId() + "@"
                        + binding.version() + " has a numerical/reference binding identity mismatch for model "
                        + binding.modelId() + " (" + binding.numericalSolverId() + " / "
                        + binding.referenceSolverId() + "): " + mismatch.getMessage(), mismatch);
            }
        }
    }

    /** Numeric version components sort numerically; textual components use case-insensitive lexical order. */
    static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[._-]", -1);
        String[] rightParts = right.split("[._-]", -1);
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            String leftPart = index < leftParts.length ? leftParts[index] : "0";
            String rightPart = index < rightParts.length ? rightParts[index] : "0";
            int order = comparePart(leftPart, rightPart);
            if (order != 0) return order;
        }
        return 0;
    }

    private static int comparePart(String left, String right) {
        try {
            return new BigInteger(left).compareTo(new BigInteger(right));
        } catch (NumberFormatException ignored) {
            return left.compareToIgnoreCase(right);
        }
    }

    private static IllegalStateException missingBinding(CatalogBinding binding) {
        return new IllegalStateException("Approved latest schema " + binding.schemaId() + "@" + binding.version()
                + " (model " + binding.modelId() + ") has no typed PhysicsModuleRegistry binding for numerical/reference IDs "
                + binding.numericalSolverId() + " / " + binding.referenceSolverId()
                + "; register one PhysicsModule for this solver pair before publishing the schema version");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
