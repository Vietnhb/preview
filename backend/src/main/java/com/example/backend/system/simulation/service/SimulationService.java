package com.example.backend.system.simulation.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.model.enums.Visibility;
import com.example.backend.system.library.repository.LibraryItemRepository;
import com.example.backend.system.simulation.dto.ResolvedEnd;
import com.example.backend.system.simulation.dto.SimulationResponse;
import com.example.backend.system.simulation.dto.SimulationSummaryResponse;
import com.example.backend.system.simulation.dto.ValidationResponse;
import com.example.backend.system.simulation.model.entity.Simulation;
import com.example.backend.system.simulation.model.entity.SimulationRun;
import com.example.backend.system.simulation.repository.SimulationRepository;
import com.example.backend.system.simulation.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/replay access for persisted simulations; creation and physical execution use the schema-driven simulation runtime. */
@Service
public class SimulationService {
    private final SimulationRepository simulations;
    private final SimulationRunRepository runs;
    private final LibraryItemRepository library;
    private final CurrentUserService currentUser;
    private final ObjectMapper json;
    private final AccountAccessService access;

    public SimulationService(SimulationRepository simulations, SimulationRunRepository runs,
            LibraryItemRepository library, CurrentUserService currentUser, ObjectMapper json, AccountAccessService access) {
        this.simulations = simulations;
        this.runs = runs;
        this.library = library;
        this.currentUser = currentUser;
        this.json = json;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public SimulationResponse get(UUID id) {
        User user = currentUser.requireCurrentUser();
        Simulation simulation = simulations.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Simulation not found"));
        return latestResponse(simulation);
    }

    public UUID latestRunId(Simulation simulation) {
        return runs.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())
                .map(SimulationRun::getId).orElse(null);
    }

    public UUID runIdAtOrBefore(Simulation simulation, Instant timestamp) {
        if (timestamp == null) return latestRunId(simulation);
        return runs.findFirstBySimulationIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(simulation.getId(), timestamp)
                .map(SimulationRun::getId).orElseGet(() -> latestRunId(simulation));
    }

    @Transactional(readOnly = true)
    public SimulationResponse replay(Simulation simulation, UUID runId) {
        if (runId == null) return latestResponse(simulation);
        SimulationRun run = runs.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Assigned simulation run not found"));
        if (run.getSimulation() == null || !simulation.getId().equals(run.getSimulation().getId()))
            throw ApiException.forbidden("Assigned simulation run is not available");
        return response(simulation, run);
    }

    @Transactional(readOnly = true)
    public SimulationResponse getShared(UUID id) {
        return latestResponse(sharedSimulation(id));
    }

    /**
     * The scene exactly as its author built it (illustration, timeline, parameters), for viewers of a
     * shared item. Access is the same as {@link #getShared}; older saves without a scene answer 404.
     */
    @Transactional(readOnly = true)
    public JsonNode getSharedGenerated(UUID id) {
        Simulation simulation = sharedSimulation(id);
        JsonNode result = runs.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())
                .map(SimulationRun::getResult).orElse(null);
        if (result == null || !result.path("simulationSpec").path("visualProgram").isObject())
            throw ApiException.notFound("Mô phỏng này chưa có cảnh minh họa đã lưu.");
        return result.deepCopy();
    }

    private Simulation sharedSimulation(UUID id) {
        User user = currentUser.currentUserOrNull();
        LibraryItem reviewItem = null;
        if (access.canReviewPublic(user)) {
            reviewItem = library.findBySimulationIdAndVisibilityOrderByCreatedAtDesc(id, Visibility.PUBLIC).stream()
                    .filter(this::canPreviewModerationItem).findFirst().orElse(null);
        } else if (access.isDepartmentHead(user) && user.getSchool() != null) {
            reviewItem = library.findBySimulationIdAndVisibilityOrderByCreatedAtDesc(id, Visibility.SHARED).stream()
                    .filter(this::canPreviewModerationItem)
                    .filter(item -> item.getOwner() != null && item.getOwner().getSchool() != null
                            && user.getSchool().getId().equals(item.getOwner().getSchool().getId())
                            && (item.getSharedInstitutionId() == null || item.getSharedInstitutionId().isBlank()
                                || user.getInstitutionId().equals(item.getSharedInstitutionId())))
                    .findFirst().orElse(null);
        }
        if (reviewItem != null)
            return reviewItem.getSimulation();
        LibraryItem item = library.findVisiblePublishedSimulation(id,
                user == null ? Set.of(Visibility.PUBLIC) : Set.of(Visibility.SHARED, Visibility.PUBLIC), Visibility.PUBLIC,
                Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), user == null ? null : user.getInstitutionId())
                .orElseThrow(() -> ApiException.notFound("Shared simulation not found"));
        return item.getSimulation();
    }

    private boolean canPreviewModerationItem(LibraryItem item) {
        return item.getSimulation() != null && (item.isActive() || item.getModerationStatus() == LibraryModerationStatus.REMOVED);
    }

    @Transactional(readOnly = true)
    public List<SimulationResponse> history() {
        User user = currentUser.requireCurrentUser();
        return simulations.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream().map(this::latestResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SimulationSummaryResponse> recent() {
        User user = currentUser.requireCurrentUser();
        return simulations.findSummariesByOwnerId(user.getId(), PageRequest.of(0, 6)).stream()
                .map(row -> new SimulationSummaryResponse(row.getSimulationId(), row.getSpecificationId(),
                        firstText(row.getEditableText(), row.getOriginalText(), row.getSchemaId()), row.getSchemaId(),
                        row.getStatus(), row.getCreatedAt())).toList();
    }

    /** Physical parameter edits are disabled until a schema-backed execution binding is available. */
    @Transactional(readOnly = true)
    public SimulationResponse previewAdjustment(Simulation simulation, UUID baseRunId, Map<String, Double> requestedParams) {
        throw ApiException.gone("Parameter execution is unavailable until this topic has a published solver binding");
    }

    private SimulationResponse latestResponse(Simulation simulation) {
        return runs.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())
                .map(run -> response(simulation, run))
                .orElseGet(() -> response(simulation, null));
    }

    private SimulationResponse response(Simulation simulation, SimulationRun run) {
        JsonNode result = run == null || run.getResult() == null ? json.createObjectNode() : run.getResult();
        JsonNode validationNode = result.path("validation");
        boolean passed = run != null && run.isValidationPassed();
        ValidationResponse validation = new ValidationResponse(run == null ? null : run.getId(), passed,
                simulation.getSchemaId(), validationNode.path("tolerance").asDouble(0), List.of(),
                validationNode.path("errors").findValuesAsText("message"), 0);
        JsonNode end = result.path("resolvedEnd");
        ResolvedEnd resolved = end.isObject() ? new ResolvedEnd(end.path("time").asDouble(0),
                end.path("reason").asText("stored"), end.path("conditionReached").asBoolean(false)) : null;
        return new SimulationResponse(simulation.getId(), run == null ? null : run.getId(),
                simulation.getSpecification().getId(), simulation.getSchemaId(), passed, passed,
                doubles(result.path("time")), series(result.path("positions")), series(result.path("velocities")),
                series(result.path("accelerations")), series(result.path("values")), doublesMap(result.path("scalarOutputs")),
                nodeMap(result.path("scalarFields")), doublesMap(result.path("parameters")),
                result.path("visualization"), validation, result, resolved, 0,
                run == null ? "No persisted simulation run" : "Stored simulation replay");
    }

    private List<Double> doubles(JsonNode node) {
        if (!node.isArray()) return List.of();
        java.util.ArrayList<Double> values = new java.util.ArrayList<>();
        node.forEach(value -> { if (value.isNumber() && Double.isFinite(value.asDouble())) values.add(value.asDouble()); });
        return List.copyOf(values);
    }

    private Map<String, List<Double>> series(JsonNode node) {
        if (!node.isObject()) return Map.of();
        java.util.LinkedHashMap<String, List<Double>> result = new java.util.LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), doubles(entry.getValue())));
        return Map.copyOf(result);
    }

    private Map<String, Double> doublesMap(JsonNode node) {
        if (!node.isObject()) return Map.of();
        java.util.LinkedHashMap<String, Double> result = new java.util.LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> { if (entry.getValue().isNumber()) result.put(entry.getKey(), entry.getValue().asDouble()); });
        return Map.copyOf(result);
    }

    private Map<String, JsonNode> nodeMap(JsonNode node) {
        if (!node.isObject()) return Map.of();
        java.util.LinkedHashMap<String, JsonNode> result = new java.util.LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().deepCopy()));
        return Map.copyOf(result);
    }

    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "Simulation";
    }
}
