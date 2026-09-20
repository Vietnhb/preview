package com.example.backend.physics.compatibility;

import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.reference.ReferenceSolver;
import com.example.backend.physics.reference.ReferenceSolverRegistry;
import com.example.backend.physics.solver.PhysicsSolver;
import com.example.backend.physics.solver.PhysicsSolverRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

/**
 * Versioned boundary for the historical raw-JSON physics interfaces.
 * Permits are loaded from non-latest source versions and the immutable archived
 * catalog. They bind one exact schema version to one exact solver-binding
 * version and numerical/reference pair.
 */
@Component
public final class LegacyPhysicsExecutionAdapterV1 {
    public static final String VERSION = "legacy-physics-execution-v1";
    private static final String ACTIVE_CATALOG_RESOURCE = "schemas/catalog.json";
    private static final String HISTORY_CATALOG_RESOURCE = "schemas/history/published-versions.json";

    private final Set<Permit> permits;

    @Autowired
    public LegacyPhysicsExecutionAdapterV1(ObjectMapper objectMapper) {
        this(loadHistoricalPermits(objectMapper));
    }

    /** Constructor for focused tests and explicit adapter data composition. */
    public LegacyPhysicsExecutionAdapterV1(Collection<Permit> permits) {
        Objects.requireNonNull(permits, "permits");
        Set<Permit> validated = new HashSet<>();
        for (Permit permit : permits) {
            if (!validated.add(Objects.requireNonNull(permit, "permit"))) {
                throw new IllegalStateException("Duplicate legacy physics execution permit: " + permit);
            }
        }
        this.permits = Set.copyOf(validated);
    }

    public AuthorizedNumericalSolver authorizeNumerical(String adapterVersion,
            PinnedExecution pinned, boolean latestApprovedVersion, PhysicsSolverRegistry registry) {
        authorize(adapterVersion, pinned, latestApprovedVersion);
        PhysicsSolver solver = requireNumerical(registry, pinned.numericalSolverId());
        if (!pinned.numericalSolverId().equals(solver.solverId())) {
            throw new SolverBindingException("Legacy numerical solver registry returned a different solver for "
                    + pinned.schemaId() + "@" + pinned.schemaVersion());
        }
        return new AuthorizedNumericalSolver(pinned, solver);
    }

    public AuthorizedReferenceSolver authorizeReference(String adapterVersion,
            PinnedExecution pinned, boolean latestApprovedVersion, ReferenceSolverRegistry registry) {
        authorize(adapterVersion, pinned, latestApprovedVersion);
        ReferenceSolver solver = requireReference(registry, pinned.referenceSolverId());
        if (!pinned.referenceSolverId().equals(solver.solverId())) {
            throw new SolverBindingException("Legacy reference solver registry returned a different solver for "
                    + pinned.schemaId() + "@" + pinned.schemaVersion());
        }
        return new AuthorizedReferenceSolver(pinned, solver);
    }

    public boolean permits(PinnedExecution pinned) {
        return permits.contains(Permit.from(pinned));
    }

    private void authorize(String adapterVersion, PinnedExecution pinned, boolean latestApprovedVersion) {
        Objects.requireNonNull(pinned, "pinned");
        if (!VERSION.equals(adapterVersion)) {
            throw new SolverBindingException("Unsupported legacy physics execution adapter version: "
                    + adapterVersion);
        }
        if (latestApprovedVersion) {
            throw new SolverBindingException("Latest approved schema " + pinned.schemaId() + "@"
                    + pinned.schemaVersion() + " has no typed physics module; raw legacy execution is disabled");
        }
        if (!permits.contains(Permit.from(pinned))) {
            throw new SolverBindingException("No " + VERSION + " permit matches pinned historical schema/binding "
                    + pinned.schemaId() + "@" + pinned.schemaVersion() + " bindingVersion="
                    + pinned.bindingVersion() + " solvers=" + pinned.numericalSolverId() + "/"
                    + pinned.referenceSolverId());
        }
    }

    private static PhysicsSolver requireNumerical(PhysicsSolverRegistry registry, String solverId) {
        try {
            return Objects.requireNonNull(registry, "registry").get(solverId);
        } catch (RuntimeException exception) {
            throw new SolverBindingException("Legacy numerical solver is unavailable for pinned binding: "
                    + solverId, exception);
        }
    }

    private static ReferenceSolver requireReference(ReferenceSolverRegistry registry, String solverId) {
        try {
            return Objects.requireNonNull(registry, "registry").get(solverId);
        } catch (RuntimeException exception) {
            throw new SolverBindingException("Legacy reference solver is unavailable for pinned binding: "
                    + solverId, exception);
        }
    }

    private static Set<Permit> loadHistoricalPermits(ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "objectMapper");
        try {
            JsonNode activeEntries = readArray(mapper, ACTIVE_CATALOG_RESOURCE);
            JsonNode historicalEntries = readArray(mapper, HISTORY_CATALOG_RESOURCE);
            Map<String, String> latestActiveVersion = new HashMap<>();
            for (JsonNode entry : activeEntries) {
                String id = text(entry, "schemaId").toLowerCase(Locale.ROOT);
                String version = text(entry, "version");
                latestActiveVersion.merge(id, version,
                        (left, right) -> compareVersions(left, right) >= 0 ? left : right);
            }
            Set<SchemaIdentity> latestActiveIdentities = latestActiveVersion.entrySet().stream()
                    .map(entry -> new SchemaIdentity(entry.getKey(), entry.getValue()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<Permit> permits = new HashSet<>();
            for (JsonNode entry : activeEntries) {
                Permit permit = permit(entry);
                if (!latestActiveIdentities.contains(identity(permit))) {
                    addUnique(permits, permit, ACTIVE_CATALOG_RESOURCE);
                }
            }
            for (JsonNode entry : historicalEntries) {
                Permit permit = permit(entry);
                if (!latestActiveIdentities.contains(identity(permit))) {
                    addUnique(permits, permit, HISTORY_CATALOG_RESOURCE);
                }
            }
            return Set.copyOf(permits);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load versioned legacy physics permit catalog: "
                    + HISTORY_CATALOG_RESOURCE, exception);
        }
    }

    private static JsonNode readArray(ObjectMapper mapper, String resource) throws IOException {
        JsonNode entries;
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            entries = mapper.readTree(input);
        }
        if (entries == null || !entries.isArray()) {
            throw new IllegalStateException("Legacy physics permit source must be a JSON array: " + resource);
        }
        return entries;
    }

    private static Permit permit(JsonNode entry) {
        String schemaId = text(entry, "schemaId");
        String schemaVersion = text(entry, "version");
        return new Permit(schemaId, schemaVersion, schemaVersion,
                text(entry, "solverId"), text(entry, "referenceSolverId"));
    }

    private static void addUnique(Set<Permit> permits, Permit permit, String source) {
        if (!permits.add(permit)) {
            throw new IllegalStateException("Duplicate legacy physics permit in " + source + ": " + permit);
        }
    }

    private static int compareVersions(String left, String right) {
        List<String> leftParts = List.of(left.split("[._-]", -1));
        List<String> rightParts = List.of(right.split("[._-]", -1));
        for (int index = 0; index < Math.max(leftParts.size(), rightParts.size()); index++) {
            String leftPart = index < leftParts.size() ? leftParts.get(index) : "0";
            String rightPart = index < rightParts.size() ? rightParts.get(index) : "0";
            int order;
            try {
                order = new BigInteger(leftPart).compareTo(new BigInteger(rightPart));
            } catch (NumberFormatException ignored) {
                order = leftPart.compareToIgnoreCase(rightPart);
            }
            if (order != 0) return order;
        }
        return 0;
    }

    private record SchemaIdentity(String schemaId, String schemaVersion) { }

    private static SchemaIdentity identity(Permit permit) {
        return new SchemaIdentity(permit.schemaId().toLowerCase(Locale.ROOT), permit.schemaVersion());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("Legacy physics permit catalog entry is missing " + field);
        }
        return value.asText();
    }

    public record PinnedExecution(String schemaId, String schemaVersion, String bindingVersion,
                                  String numericalSolverId, String referenceSolverId) {
        public PinnedExecution {
            schemaId = required(schemaId, "schemaId");
            schemaVersion = required(schemaVersion, "schemaVersion");
            bindingVersion = required(bindingVersion, "bindingVersion");
            numericalSolverId = required(numericalSolverId, "numericalSolverId");
            referenceSolverId = required(referenceSolverId, "referenceSolverId");
        }

    }

    public record Permit(String schemaId, String schemaVersion, String bindingVersion,
                         String numericalSolverId, String referenceSolverId) {
        public Permit {
            schemaId = required(schemaId, "schemaId");
            schemaVersion = required(schemaVersion, "schemaVersion");
            bindingVersion = required(bindingVersion, "bindingVersion");
            numericalSolverId = required(numericalSolverId, "numericalSolverId");
            referenceSolverId = required(referenceSolverId, "referenceSolverId");
        }

        private static Permit from(PinnedExecution pinned) {
            return new Permit(pinned.schemaId(), pinned.schemaVersion(), pinned.bindingVersion(),
                    pinned.numericalSolverId(), pinned.referenceSolverId());
        }
    }

    public static final class AuthorizedNumericalSolver {
        private final PinnedExecution pinned;
        private final PhysicsSolver delegate;

        private AuthorizedNumericalSolver(PinnedExecution pinned, PhysicsSolver delegate) {
            this.pinned = pinned;
            this.delegate = delegate;
        }

        public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                  double durationSeconds, double stepSeconds) {
            if (!pinned.numericalSolverId().equals(delegate.solverId())) {
                throw new SolverBindingException("Authorized numerical solver no longer matches pinned binding for "
                        + pinned.schemaId() + "@" + pinned.schemaVersion());
            }
            return delegate.solve(specification, overrides, durationSeconds, stepSeconds);
        }
    }

    public static final class AuthorizedReferenceSolver {
        private final PinnedExecution pinned;
        private final ReferenceSolver delegate;

        private AuthorizedReferenceSolver(PinnedExecution pinned, ReferenceSolver delegate) {
            this.pinned = pinned;
            this.delegate = delegate;
        }

        public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
            if (!pinned.referenceSolverId().equals(delegate.solverId())) {
                throw new SolverBindingException("Authorized reference solver no longer matches pinned binding for "
                        + pinned.schemaId() + "@" + pinned.schemaVersion());
            }
            return delegate.solve(specification, overrides, timeSeconds);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
