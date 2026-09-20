package com.example.backend.service.simulation;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.SpecificationReadinessService;

import com.example.backend.dto.simulation.ParameterAdjustmentRequest;
import com.example.backend.dto.simulation.SimulationRequest;
import com.example.backend.dto.simulation.SimulationResponse;
import com.example.backend.dto.simulation.SimulationSummaryResponse;
import com.example.backend.dto.simulation.ValidationResponse;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.entity.simulation.SimulationRun;
import com.example.backend.entity.enums.SimulationStatus;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.exception.ApiException;
import com.example.backend.physics.solver.PhysicsSolver;
import com.example.backend.physics.solver.PhysicsSolverRegistry;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.physics.validation.OutputContractValidator;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.dto.simulation.ResolvedEnd;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SimulationService {
    private static final String PARAMETERS = "parameters";
    private static final String VALIDATION = "validation";
    private final SimulationRepository simulationRepository;
    private final LibraryItemRepository libraryItemRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final SpecificationRepository specificationRepository;
    private final PhysicsSolverRegistry solverRegistry;
    private final PhysicsValidationService validationService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final SchemaDefinitionService schemaDefinitions;
    private final SpecificationReadinessService readinessService;

    private record CalculationContext(JsonNode input, String schemaId, String schemaVersion,
                                      Map<String, Double> params, double duration, double step, String runType) { }

    private record CalculationResult(SolverOutput output, ResolvedEnd resolvedEnd) { }

    @Transactional
    public SimulationResponse run(SimulationRequest request) {
        User user = currentUserService.requireCurrentUser();
        Specification specification = requireOwnedSpecification(request.specificationId(), user);
        assertReady(specification);
        String schemaId = specification.getSchemaId();
        JsonNode input = readinessService.toJson(specification);
        Map<String, Double> requestedParams = request.adjustableParams() == null
                ? Map.of() : new LinkedHashMap<>(request.adjustableParams());
        SchemaVersion schema = schemaDefinitions.requireApproved(schemaId, specification.getSchemaVersion());
        assertAllowedAdjustments(schema, requestedParams);
        Map<String, Double> params = schemaDefinitions.effectiveAdjustments(input, schema.getDefinition(), requestedParams, Map.of());
        JsonNode execution = schema.getDefinition().path("execution");
        double duration = schemaDefinitions.durationSeconds(input, schema.getDefinition());
        double step = execution.path("stepSeconds").asDouble();

        Simulation simulation = new Simulation();
        simulation.setSpecification(specification);
        simulation.setOwner(user);
        simulation.setSchemaId(schemaId);
        var binding = schemaDefinitions.requireSolverBinding(schemaId, specification.getSchemaVersion());
        simulation.setSolverVersion(binding.version() + ":" + binding.numericalSolverId());
        simulation.setStatus(SimulationStatus.VALIDATING);
        simulation = simulationRepository.save(simulation);
        return calculateAndPersist(simulation, new CalculationContext(
                input, schemaId, specification.getSchemaVersion(), params, duration, step, "INITIAL"));
    }

    @Transactional
    public SimulationResponse adjust(ParameterAdjustmentRequest request) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByIdAndOwnerId(request.simulationId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Simulation not found"));
        assertReady(simulation.getSpecification());
        JsonNode input = readinessService.toJson(simulation.getSpecification());
        SchemaVersion schema = schemaDefinitions.requireApproved(
                simulation.getSchemaId(), simulation.getSpecification().getSchemaVersion());
        Map<String, Double> requestedParams = request.adjustableParams() == null
                ? Map.of() : new LinkedHashMap<>(request.adjustableParams());
        assertAllowedAdjustments(schema, requestedParams);
        Map<String, Double> previousParams = mapNumbers(latestResult(simulation), PARAMETERS);
        Map<String, Double> params = schemaDefinitions.effectiveAdjustments(input, schema.getDefinition(), requestedParams, previousParams);
        List<Double> previousTime = list(latestResult(simulation), "time");
        if (previousTime.size() < 2) throw new ApiException(HttpStatus.CONFLICT, "Previous simulation timeline is unavailable");
        // Re-read the persisted execution contract. The previous resolved
        // duration is an output, not the next run's input horizon.
        double duration = schemaDefinitions.durationSeconds(input, schema.getDefinition());
        double step = schema.getDefinition().path("execution").path("stepSeconds").asDouble();
        return calculateAndPersist(simulation, new CalculationContext(
                input, simulation.getSchemaId(), simulation.getSpecification().getSchemaVersion(),
                params, duration, step, "ADJUSTMENT"));
    }

    /**
     * Calculates a student-side variation without changing the teacher-owned
     * simulation or creating a new persisted run. The assignment's captured
     * run remains the source of truth for replay.
     */
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public SimulationResponse previewAdjustment(Simulation simulation, UUID baseRunId,
                                                Map<String, Double> requestedParams) {
        if (simulation == null || simulation.getSpecification() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assigned simulation not found");
        }
        assertReady(simulation.getSpecification());
        JsonNode input = readinessService.toJson(simulation.getSpecification());
        String schemaVersion = simulation.getSpecification().getSchemaVersion();
        SchemaVersion schema = schemaDefinitions.requireApproved(simulation.getSchemaId(), schemaVersion);
        Map<String, Double> requested = requestedParams == null
                ? Map.of() : new LinkedHashMap<>(requestedParams);
        assertAllowedAdjustments(schema, requested);

        JsonNode baseResult = latestResult(simulation);
        if (baseRunId != null) {
            SimulationRun baseRun = simulationRunRepository.findById(baseRunId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assigned simulation run not found"));
            if (baseRun.getSimulation() == null || !simulation.getId().equals(baseRun.getSimulation().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Assigned simulation run is not available");
            }
            baseResult = baseRun.getResult();
        }

        Map<String, Double> previousParams = mapNumbers(baseResult, PARAMETERS);
        Map<String, Double> params = schemaDefinitions.effectiveAdjustments(input, schema.getDefinition(), requested, previousParams);
        List<Double> previousTime = list(baseResult, "time");
        if (previousTime.size() < 2) {
            throw new ApiException(HttpStatus.CONFLICT, "Assigned simulation timeline is unavailable");
        }
        double duration = schemaDefinitions.durationSeconds(input, schema.getDefinition());
        double step = schema.getDefinition().path("execution").path("stepSeconds").asDouble();
        long started = System.nanoTime();
        CalculationResult calculation;
        try {
            calculation = solveWithEndCondition(input, simulation.getSchemaId(), schemaVersion, params, duration, step);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Simulation failed: " + exception.getMessage());
        }
        SolverOutput output = calculation.output();
        ResolvedEnd resolvedEnd = calculation.resolvedEnd();
        ValidationResponse validation = validationService.validate(
                input, simulation.getSchemaId(), schemaVersion, output, params);
        JsonNode result = resultJson(output, params, validation, resolvedEnd);
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        return new SimulationResponse(simulation.getId(), baseRunId, simulation.getSpecification().getId(),
                simulation.getSchemaId(), validation.passed(), validation.passed(), output.time(),
                output.positions(), output.velocities(), output.accelerations(), output.values(), output.scalarFields(), params,
                visualization(simulation.getSchemaId(), schemaVersion), validation, result, resolvedEnd, elapsed,
                validation.passed() ? "Preview adjustment passed" : "Preview adjustment failed validation");
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public SimulationResponse get(UUID id) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Simulation not found"));
        return latestResponse(simulation, true);
    }

    public SimulationResponse latestFor(Simulation simulation) {
        return latestResponse(simulation, true);
    }

    /** Captures the run that a teacher shared so later adjustments do not change student replay. */
    public UUID latestRunId(Simulation simulation) {
        return simulationRunRepository.findBySimulationIdOrderByCreatedAtDesc(simulation.getId()).stream()
                .findFirst().map(SimulationRun::getId).orElse(null);
    }

    public UUID runIdAtOrBefore(Simulation simulation, Instant timestamp) {
        return simulationRunRepository.findBySimulationIdOrderByCreatedAtDesc(simulation.getId()).stream()
                .filter(run -> timestamp == null || run.getCreatedAt() == null || !run.getCreatedAt().isAfter(timestamp))
                .findFirst().map(SimulationRun::getId).orElseGet(() -> latestRunId(simulation));
    }

    @Transactional(readOnly = true)
    public SimulationResponse replay(Simulation simulation, UUID runId) {
        if (runId == null) return latestResponse(simulation, true);
        SimulationRun run = simulationRunRepository.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assigned simulation run not found"));
        if (run.getSimulation() == null || !simulation.getId().equals(run.getSimulation().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assigned simulation run is not available");
        }
        return responseFromResult(simulation, run.getResult(), run.getId(), run.isValidationPassed(), "REPLAY");
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public SimulationResponse getShared(UUID id) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = libraryItemRepository
                .findVisiblePublishedSimulation(id, java.util.Set.of(Visibility.SHARED, Visibility.PUBLIC), Visibility.PUBLIC,
                        java.util.Set.of(com.example.backend.entity.enums.LibraryModerationStatus.APPROVED,
                                com.example.backend.entity.enums.LibraryModerationStatus.FEATURED), user.getInstitutionId())
                .map(item -> item.getSimulation())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shared simulation not found"));
        return latestResponse(simulation, true);
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public List<SimulationResponse> history() {
        User user = currentUserService.requireCurrentUser();
        List<Simulation> simulations = simulationRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId());
        if (simulations.isEmpty()) return List.of();
        Map<UUID, UUID> latestRunIds = new HashMap<>();
        simulationRunRepository.findLatestRunIds(simulations.stream().map(Simulation::getId).toList())
                .forEach(item -> latestRunIds.put(item.getSimulationId(), item.getRunId()));
        return simulations.stream()
                .map(simulation -> latestResponse(simulation, latestRunIds.get(simulation.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SimulationSummaryResponse> recent() {
        User user = currentUserService.requireCurrentUser();
        return simulationRepository.findSummariesByOwnerId(user.getId()).stream()
                .map(item -> new SimulationSummaryResponse(item.getSimulationId(), item.getSpecificationId(),
                        item.getSchemaId(), item.getStatus(), item.getCreatedAt()))
                .toList();
    }

    private SimulationResponse calculateAndPersist(Simulation simulation, CalculationContext context) {
        long started = System.nanoTime();
        CalculationResult calculation;
        try {
            calculation = solveWithEndCondition(context.input(), context.schemaId(), context.schemaVersion(),
                    context.params(), context.duration(), context.step());
        } catch (RuntimeException exception) {
            simulation.setStatus(SimulationStatus.FAILED);
            simulationRepository.save(simulation);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Simulation failed: " + exception.getMessage());
        }
        SolverOutput output = calculation.output();
        ResolvedEnd resolvedEnd = calculation.resolvedEnd();
        ValidationResponse validation = validationService.validate(
                context.input(), context.schemaId(), context.schemaVersion(), output, context.params());
        JsonNode result = resultJson(output, context.params(), validation, resolvedEnd);
        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);
        run.setRunType(context.runType());
        run.setValidationPassed(validation.passed());
        run.setValidationCheckpoints(objectMapper.valueToTree(validation.checkpoints()));
        run.setValidationError(validation.errors().isEmpty() ? null : String.join("; ", validation.errors()));
        run.setDurationSeconds(output.time().isEmpty() ? 0 : output.time().get(output.time().size() - 1));
        run.setResult(result);
        simulationRunRepository.save(run);
        validation = new ValidationResponse(run.getId(), validation.passed(), validation.schemaId(),
                validation.tolerance(), validation.checkpoints(), validation.errors(), validation.validationTimeMs());
        result = resultJson(output, context.params(), validation, resolvedEnd);
        run.setResult(result);
        simulation.setLatestRun(run);
        simulation.setStatus(validation.passed() ? SimulationStatus.READY : SimulationStatus.BLOCKED);
        simulationRepository.save(simulation);
        specificationStatus(simulation.getSpecification(), validation, result);
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        return new SimulationResponse(simulation.getId(), run.getId(), simulation.getSpecification().getId(), context.schemaId(),
                validation.passed(), validation.passed(), output.time(), output.positions(), output.velocities(),
                output.accelerations(), output.values(), output.scalarFields(), context.params(),
                visualization(context.schemaId(), context.schemaVersion()), validation, result, resolvedEnd, elapsed,
                validation.passed() ? "Validation passed" : "Simulation blocked because validation failed");
    }

    private CalculationResult solveWithEndCondition(JsonNode input, String schemaId, String schemaVersion,
                                                     Map<String, Double> params, double fallbackDuration,
                                                     double step) {
        JsonNode condition = EndConditionResolver.normalize(input, fallbackDuration);
        double requestedHorizon = Math.max(0.01, EndConditionResolver.initialHorizon(condition, fallbackDuration));
        double horizon = "time_limit".equals(condition.path("type").asText())
                ? requestedHorizon
                : Math.min(EndConditionResolver.MAX_DYNAMIC_SECONDS, requestedHorizon);
        PhysicsSolver solver = solverRegistry.get(
                schemaDefinitions.requireSolverBinding(schemaId, schemaVersion).numericalSolverId());
        SolverOutput output = solver.solve(input, params, horizon, step);
        OutputContractValidator.validate(schemaId,
                schemaDefinitions.requireApproved(schemaId, schemaVersion).getDefinition(), output);
        EndConditionResolver.ResolvedEnd resolved = EndConditionResolver.resolve(condition, output);

        // Dynamic conditions may need more samples to discover a future
        // crossing/period. Grow only to a bounded engine horizon; no solver
        // loop can run indefinitely.
        while (EndConditionResolver.expandable(condition, resolved, horizon)) {
            double nextHorizon = Math.min(EndConditionResolver.MAX_DYNAMIC_SECONDS,
                    Math.max(horizon + Math.max(step, 0.01), horizon * 2));
            if (nextHorizon <= horizon) break;
            horizon = nextHorizon;
            output = solver.solve(input, params, horizon, step);
            OutputContractValidator.validate(schemaId,
                    schemaDefinitions.requireApproved(schemaId, schemaVersion).getDefinition(), output);
            resolved = EndConditionResolver.resolve(condition, output);
        }

        ResolvedEnd response = new ResolvedEnd(resolved.time(), resolved.reason(), resolved.conditionReached());
        return new CalculationResult(EndConditionResolver.trim(output, response.time()), response);
    }

    private SimulationResponse latestResponse(Simulation simulation, boolean includeRunId) {
        JsonNode latest = latestResult(simulation);
        boolean ready = simulation.getStatus() == SimulationStatus.READY
                || simulation.getStatus() == SimulationStatus.ARCHIVED;
        String status = simulation.getStatus() == null ? "UNKNOWN" : simulation.getStatus().name();
        UUID latestRunId = includeRunId
                ? simulationRunRepository.findBySimulationIdOrderByCreatedAtDesc(simulation.getId()).stream()
                        .findFirst().map(SimulationRun::getId).orElse(null)
                : null;
        return responseFromResult(simulation, latest, latestRunId, ready, status);
    }

    private SimulationResponse latestResponse(Simulation simulation, UUID runId) {
        JsonNode latest = latestResult(simulation);
        boolean ready = simulation.getStatus() == SimulationStatus.READY
                || simulation.getStatus() == SimulationStatus.ARCHIVED;
        String status = simulation.getStatus() == null ? "UNKNOWN" : simulation.getStatus().name();
        return responseFromResult(simulation, latest, runId, ready, status);
    }

    private SimulationResponse responseFromResult(Simulation simulation, JsonNode result, UUID runId,
                                                   boolean ready, String message) {
        Map<String, Double> parameters = mapNumbers(result, PARAMETERS);
        if (parameters.isEmpty()) parameters = historicalParameters(simulation);
        ResolvedEnd resolvedEnd = resolvedEndFromResult(result);
        return new SimulationResponse(simulation.getId(), runId, simulation.getSpecification().getId(), simulation.getSchemaId(),
                ready, ready, list(result, "time"), map(result, "positions"), map(result, "velocities"),
                map(result, "accelerations"), map(result, "values"), scalarFields(result), parameters,
                safeVisualization(simulation.getSchemaId(), simulation.getSpecification().getSchemaVersion()),
                validationFromResult(result), result, resolvedEnd, 0, message);
    }

    private JsonNode latestResult(Simulation simulation) {
        return simulation.getLatestRun() == null ? null : simulation.getLatestRun().getResult();
    }

    private ResolvedEnd resolvedEndFromResult(JsonNode result) {
        if (result != null && result.get("resolvedEnd") != null && !result.get("resolvedEnd").isNull()) {
            try {
                return objectMapper.treeToValue(result.get("resolvedEnd"), ResolvedEnd.class);
            } catch (Exception ignored) {
                // Legacy result payloads are completed below.
            }
        }
        List<Double> times = list(result, "time");
        double end = times.isEmpty() ? 0 : times.get(times.size() - 1);
        return new ResolvedEnd(end, "time_limit", !times.isEmpty());
    }

    private Map<String, Double> historicalParameters(Simulation simulation) {
        try {
            SchemaVersion schema = schemaDefinitions.requireHistorical(
                    simulation.getSchemaId(), simulation.getSpecification().getSchemaVersion());
            return schemaDefinitions.effectiveAdjustments(
                    readinessService.toJson(simulation.getSpecification()), schema.getDefinition(), Map.of(), Map.of());
        } catch (RuntimeException ignored) {
            // Legacy runs without a persisted snapshot stay readable, but no
            // guessed value is returned when the old schema cannot prove it.
            return Map.of();
        }
    }

    private ValidationResponse validationFromResult(JsonNode result) {
        if (result == null || result.get(VALIDATION) == null || result.get(VALIDATION).isNull()) return null;
        try {
            return objectMapper.treeToValue(result.get(VALIDATION), ValidationResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void specificationStatus(Specification specification, ValidationResponse validation, JsonNode result) {
        specification.setValidationStatus(validation.passed() ? "PASSED" : "FAILED");
        specification.setValidationResult(result);
        specificationRepository.save(specification);
    }

    private void assertReady(Specification specification) {
        List<String> blockers = readinessService.blockers(specification);
        if (!blockers.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,
                "Specification is not ready: " + String.join("; ", blockers));
    }

    private Specification requireOwnedSpecification(UUID id, User user) {
        return specificationRepository.findByIdAndSubmissionOwner(id, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specification not found"));
    }

    private void assertAllowedAdjustments(SchemaVersion schema, Map<String, Double> params) {
        if (params == null || params.isEmpty()) return;
        var allowed = schemaDefinitions.adjustableKeys(schema.getDefinition());
        List<String> rejected = params.keySet().stream().filter(key -> !allowed.contains(key)).toList();
        if (!rejected.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,
                "Unsupported adjustable parameters: " + String.join(", ", rejected));
        params.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Parameter " + key + " must be a finite number");
            }
        });
    }

    private JsonNode visualization(String schemaId, String schemaVersion) {
        return schemaDefinitions.visualization(
                schemaDefinitions.requireApproved(schemaId, schemaVersion).getDefinition());
    }

    /** Returns null instead of throwing when the schema version is stale/unapproved.
     *  Used by history/get to prevent one old simulation from breaking the entire list. */
    private JsonNode safeVisualization(String schemaId, String schemaVersion) {
        try { return schemaDefinitions.visualization(schemaDefinitions.requireHistorical(schemaId, schemaVersion).getDefinition()); }
        catch (Exception ignored) { return null; }
    }

    private JsonNode resultJson(SolverOutput output, Map<String, Double> params, ValidationResponse validation,
                                ResolvedEnd resolvedEnd) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("time", objectMapper.valueToTree(output.time()));
        node.set("positions", objectMapper.valueToTree(output.positions()));
        node.set("velocities", objectMapper.valueToTree(output.velocities()));
        node.set("accelerations", objectMapper.valueToTree(output.accelerations()));
        node.set("values", objectMapper.valueToTree(output.values()));
        node.set("scalarFields", objectMapper.valueToTree(output.scalarFields()));
        node.set(PARAMETERS, objectMapper.valueToTree(params));
        node.set(VALIDATION, objectMapper.valueToTree(validation));
        node.set("resolvedEnd", objectMapper.valueToTree(resolvedEnd));
        return node;
    }

    private List<Double> list(JsonNode node, String field) {
        if (node == null || node.get(field) == null) return List.of();
        return objectMapper.convertValue(node.get(field), objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
    }

    private Map<String, List<Double>> map(JsonNode node, String field) {
        if (node == null || node.get(field) == null) return Map.of();
        var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class);
        var mapType = objectMapper.getTypeFactory().constructMapType(Map.class,
                objectMapper.getTypeFactory().constructType(String.class), listType);
        return objectMapper.convertValue(node.get(field), mapType);
    }

    private Map<String, Double> mapNumbers(JsonNode node, String field) {
        if (node == null || node.get(field) == null) return Map.of();
        return objectMapper.convertValue(node.get(field), objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Double.class));
    }

    private Map<String, ScalarField> scalarFields(JsonNode node) {
        if (node == null || node.get("scalarFields") == null || !node.get("scalarFields").isObject()) return Map.of();
        var type = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, ScalarField.class);
        return objectMapper.convertValue(node.get("scalarFields"), type);
    }
}
