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
import com.example.backend.exception.CanonicalContractException;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.example.backend.exception.OutputContractException;
import com.example.backend.exception.PhysicsDomainException;
import com.example.backend.exception.SchemaCompilationException;
import com.example.backend.exception.SchemaRoutingException;
import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.physics.validation.EndConditionContract;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.physics.validation.EndConditionType;
import com.example.backend.physics.validation.LegacyEndConditionJsonAdapterV1;
import com.example.backend.physics.validation.OutputContractValidator;
import com.example.backend.physics.validation.TypedEndConditionCompiler;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.compatibility.LegacyScalarOutputSeriesAdapter;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;
import com.example.backend.physics.binding.CanonicalQuantityCompiler;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.PhysicsOutputFrameMapper;
import com.example.backend.dto.simulation.ResolvedEnd;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
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
    private final CanonicalQuantityCompiler canonicalQuantityCompiler;
    private final PhysicsModuleRegistry physicsModuleRegistry;
    private final LegacyPhysicsExecutionAdapterV1 legacyPhysicsExecution;

    private record CalculationContext(JsonNode input, String schemaId, String schemaVersion,
                                      Map<String, Double> params, double duration, double step, String runType,
                                      SchemaDefinitionService.SolverBinding binding,
                                      BoundPhysicsModule typedModule,
                                      LegacyPhysicsExecutionAdapterV1.AuthorizedNumericalSolver legacySolver,
                                      CompiledSchema compiledSchema,
                                      JsonNode schemaDefinition, EndConditionContract endCondition) { }

    private record RuntimePhysicsBinding(BoundPhysicsModule typedModule,
                                         LegacyPhysicsExecutionAdapterV1.AuthorizedNumericalSolver legacySolver) { }

    private record CalculationResult(SolverOutput output, PhysicsOutputFrame typed, ResolvedEnd resolvedEnd) { }

    private record SolvedOutput(SolverOutput legacy, PhysicsOutputFrame typed) { }

    static RuntimeException translateSimulationFailure(RuntimeException failure) {
        if (failure instanceof ApiException
                || failure instanceof CanonicalContractException
                || failure instanceof SchemaCompilationException
                || failure instanceof SchemaRoutingException
                || failure instanceof SolverBindingException
                || failure instanceof PhysicsDomainException
                || failure instanceof OutputContractException
                || failure instanceof EmbeddingUnavailableException) {
            return failure;
        }
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Simulation failed: " + failure.getMessage());
    }

    @Transactional
    public SimulationResponse run(SimulationRequest request) {
        User user = currentUserService.requireCurrentUser();
        Specification specification = requireOwnedSpecification(request.specificationId(), user);
        assertReady(specification);
        String requestedSchemaId = specification.getSchemaId();
        JsonNode input = readinessService.toJson(specification);
        Map<String, Double> requestedParams = request.adjustableParams() == null
                ? Map.of() : new LinkedHashMap<>(request.adjustableParams());
        SchemaVersion schema = schemaDefinitions.requireCurrentApproved(
                requestedSchemaId, specification.getSchemaVersion());
        String schemaId = schema.getSchemaId();
        assertAllowedAdjustments(schema, requestedParams);
        Map<String, Double> params = schemaDefinitions.effectiveAdjustments(input, schema.getDefinition(), requestedParams, Map.of());
        JsonNode execution = schema.getDefinition().path("execution");
        double duration = schemaDefinitions.durationSeconds(input, schema.getDefinition());
        double step = execution.path("stepSeconds").asDouble();
        var binding = schemaDefinitions.requireSolverBinding(schema.getSchemaId(), specification.getSchemaVersion());
        CompiledSchema compiledSchema = schemaDefinitions.compiled(schema);
        RuntimePhysicsBinding runtimeBinding = bindRuntime(compiledSchema, params, input, binding);
        EndConditionContract endCondition = compileEndCondition(
                compiledSchema, input, duration, runtimeBinding.typedModule() != null);

        Simulation simulation = new Simulation();
        simulation.setSpecification(specification);
        simulation.setOwner(user);
        simulation.setSchemaId(schemaId);
        simulation.setSolverVersion(binding.version() + ":" + binding.numericalSolverId());
        simulation.setStatus(SimulationStatus.VALIDATING);
        simulation = simulationRepository.save(simulation);
        return calculateAndPersist(simulation, new CalculationContext(
                input, schemaId, specification.getSchemaVersion(), params, duration, step, "INITIAL",
                binding,
                runtimeBinding.typedModule(), runtimeBinding.legacySolver(),
                compiledSchema, schema.getDefinition().deepCopy(), endCondition));
    }

    @Transactional
    public SimulationResponse adjust(ParameterAdjustmentRequest request) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByIdAndOwnerId(request.simulationId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Simulation not found"));
        assertReady(simulation.getSpecification());
        JsonNode input = readinessService.toJson(simulation.getSpecification());
        SchemaVersion schema = schemaDefinitions.requirePublishedVersion(
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
        var binding = schemaDefinitions.requireSolverBinding(
                schema.getSchemaId(), simulation.getSpecification().getSchemaVersion());
        CompiledSchema compiledSchema = schemaDefinitions.compiled(schema);
        RuntimePhysicsBinding runtimeBinding = bindRuntime(compiledSchema, params, input, binding);
        EndConditionContract endCondition = compileEndCondition(
                compiledSchema, input, duration, runtimeBinding.typedModule() != null);
        return calculateAndPersist(simulation, new CalculationContext(
                input, simulation.getSchemaId(), simulation.getSpecification().getSchemaVersion(),
                params, duration, step, "ADJUSTMENT", binding,
                runtimeBinding.typedModule(), runtimeBinding.legacySolver(),
                compiledSchema, schema.getDefinition().deepCopy(), endCondition));
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
        SchemaVersion schema = schemaDefinitions.requirePublishedVersion(simulation.getSchemaId(), schemaVersion);
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
        var binding = schemaDefinitions.requireSolverBinding(schema.getSchemaId(), schemaVersion);
        CompiledSchema compiledSchema = schemaDefinitions.compiled(schema);
        RuntimePhysicsBinding runtimeBinding = bindRuntime(compiledSchema, params, input, binding);
        EndConditionContract endCondition = compileEndCondition(
                compiledSchema, input, duration, runtimeBinding.typedModule() != null);
        long started = System.nanoTime();
        CalculationResult calculation;
        try {
            calculation = solveWithEndCondition(new CalculationContext(
                    input, simulation.getSchemaId(), schemaVersion, params, duration, step, null,
                    binding,
                    runtimeBinding.typedModule(), runtimeBinding.legacySolver(),
                    compiledSchema, schema.getDefinition().deepCopy(), endCondition));
        } catch (RuntimeException exception) {
            throw translateSimulationFailure(exception);
        }
        SolverOutput output = calculation.output();
        ResolvedEnd resolvedEnd = calculation.resolvedEnd();
        ValidationResponse validation = validateCalculation(input, simulation.getSchemaId(), schemaVersion,
                calculation, params, runtimeBinding.typedModule());
        JsonNode result = resultJson(output, params, validation, resolvedEnd, compiledSchema, binding);
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        return new SimulationResponse(simulation.getId(), baseRunId, simulation.getSpecification().getId(),
                simulation.getSchemaId(), validation.passed(), validation.passed(), output.time(),
                output.positions(), output.velocities(), output.accelerations(),
                LegacyScalarOutputSeriesAdapter.project(output.values(), output.scalarOutputs(), output.time().size()),
                output.scalarOutputs(),
                output.scalarFields(), params,
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
        return simulationRunRepository.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())
                .map(SimulationRun::getId).orElse(null);
    }

    public UUID runIdAtOrBefore(Simulation simulation, Instant timestamp) {
        if (timestamp == null) return latestRunId(simulation);
        return simulationRunRepository
                .findFirstBySimulationIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(simulation.getId(), timestamp)
                .map(SimulationRun::getId).orElseGet(() -> latestRunId(simulation));
    }

    @Transactional(readOnly = true)
    public SimulationResponse replay(Simulation simulation, UUID runId) {
        if (runId == null) return latestResponse(simulation, true);
        SimulationRun run = simulationRunRepository.findById(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assigned simulation run not found"));
        if (run.getSimulation() == null || !simulation.getId().equals(run.getSimulation().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assigned simulation run is not available");
        }
        assertRunIdentity(simulation, run);
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
        return simulationRepository.findSummariesByOwnerId(user.getId(), PageRequest.of(0, 6)).stream()
                .map(item -> new SimulationSummaryResponse(item.getSimulationId(), item.getSpecificationId(),
                        recentTitle(item.getEditableText(), item.getOriginalText(), item.getSchemaId()),
                        item.getSchemaId(), item.getStatus(), item.getCreatedAt()))
                .toList();
    }

    private static String recentTitle(String editableText, String originalText, String schemaId) {
        if (editableText != null && !editableText.isBlank()) return editableText.trim();
        if (originalText != null && !originalText.isBlank()) return originalText.trim();
        return schemaId;
    }

    private SimulationResponse calculateAndPersist(Simulation simulation, CalculationContext context) {
        long started = System.nanoTime();
        CalculationResult calculation;
        try {
            calculation = solveWithEndCondition(context);
        } catch (RuntimeException exception) {
            simulation.setStatus(SimulationStatus.FAILED);
            simulationRepository.save(simulation);
            throw translateSimulationFailure(exception);
        }
        SolverOutput output = calculation.output();
        ResolvedEnd resolvedEnd = calculation.resolvedEnd();
        ValidationResponse validation = validateCalculation(context.input(), context.schemaId(), context.schemaVersion(),
                calculation, context.params(), context.typedModule());
        JsonNode result = resultJson(output, context.params(), validation, resolvedEnd,
                context.compiledSchema(), context.binding());
        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);
        run.setRunType(context.runType());
        run.setSchemaId(context.schemaId());
        run.setSchemaVersion(context.schemaVersion());
        run.setBindingVersion(context.binding().version());
        run.setNumericalSolverId(context.binding().numericalSolverId());
        run.setReferenceSolverId(context.binding().referenceSolverId());
        run.setOutputContractVersion(context.schemaVersion());
        run.setOutputContractChecksum(context.compiledSchema().checksum());
        run.setValidationPassed(validation.passed());
        run.setValidationCheckpoints(objectMapper.valueToTree(validation.checkpoints()));
        run.setValidationError(validation.errors().isEmpty() ? null : String.join("; ", validation.errors()));
        run.setDurationSeconds(output.time().isEmpty() ? 0 : output.time().get(output.time().size() - 1));
        run.setResult(result);
        simulationRunRepository.save(run);
        validation = new ValidationResponse(run.getId(), validation.passed(), validation.schemaId(),
                validation.tolerance(), validation.checkpoints(), validation.errors(), validation.validationTimeMs());
        result = resultJson(output, context.params(), validation, resolvedEnd,
                context.compiledSchema(), context.binding());
        run.setResult(result);
        simulation.setLatestRun(run);
        simulation.setStatus(validation.passed() ? SimulationStatus.READY : SimulationStatus.BLOCKED);
        simulationRepository.save(simulation);
        specificationStatus(simulation.getSpecification(), validation, result);
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        return new SimulationResponse(simulation.getId(), run.getId(), simulation.getSpecification().getId(), context.schemaId(),
                validation.passed(), validation.passed(), output.time(), output.positions(), output.velocities(),
                output.accelerations(),
                LegacyScalarOutputSeriesAdapter.project(output.values(), output.scalarOutputs(), output.time().size()),
                output.scalarOutputs(), output.scalarFields(), context.params(),
                visualization(context.schemaId(), context.schemaVersion()), validation, result, resolvedEnd, elapsed,
                validation.passed() ? "Validation passed" : "Simulation blocked because validation failed");
    }

    private CalculationResult solveWithEndCondition(CalculationContext context) {
        EndConditionContract condition = context.endCondition();
        double requestedHorizon = Math.max(0.01,
                EndConditionResolver.initialHorizon(condition, context.duration()));
        double horizon = condition.type() == EndConditionType.TIME_LIMIT
                ? requestedHorizon
                : Math.min(EndConditionResolver.MAX_DYNAMIC_SECONDS, requestedHorizon);
        SolvedOutput solved = solve(context, horizon);
        SolverOutput output = solved.legacy();
        validateOutput(context, solved);
        EndConditionResolver.ResolvedEnd resolved = resolveEndCondition(context, condition, solved);

        // Dynamic conditions may need more samples to discover a future
        // crossing/period. Grow only to a bounded engine horizon; no solver
        // loop can run indefinitely.
        while (EndConditionResolver.expandable(condition, resolved, horizon)) {
            double nextHorizon = Math.min(EndConditionResolver.MAX_DYNAMIC_SECONDS,
                    Math.max(horizon + Math.max(context.step(), 0.01), horizon * 2));
            if (nextHorizon <= horizon) break;
            horizon = nextHorizon;
            solved = solve(context, horizon);
            output = solved.legacy();
            validateOutput(context, solved);
            resolved = resolveEndCondition(context, condition, solved);
        }

        ResolvedEnd response = new ResolvedEnd(resolved.time(), resolved.reason(), resolved.conditionReached());
        // End-condition trimming may add an interpolated final sample or remove
        // trailing samples. Validate the accepted shape again after that
        // transformation, immediately before the result can be persisted or
        // returned to a caller.
        SolverOutput trimmed = EndConditionResolver.trim(output, response.time());
        PhysicsOutputFrame trimmedTyped = null;
        if (context.typedModule() == null) {
            OutputContractValidator.validate(context.compiledSchema(), context.schemaDefinition(), trimmed);
        }
        if (context.typedModule() != null) {
            // End-condition trimming is performed on the typed frame. The
            // grouped output above remains only for the versioned API mapper.
            trimmedTyped = EndConditionResolver.trim(solved.typed(), response.time());
            OutputContractValidator.validate(context.compiledSchema(), trimmedTyped);
            trimmed = PhysicsOutputFrameMapper.toSolverOutput(trimmedTyped,
                    context.compiledSchema().endConditionSources());
            OutputContractValidator.validate(context.compiledSchema(), context.schemaDefinition(), trimmed,
                    context.compiledSchema().endConditionSources());
        }
        return new CalculationResult(trimmed, trimmedTyped, response);
    }

    private EndConditionResolver.ResolvedEnd resolveEndCondition(CalculationContext context,
            EndConditionContract condition, SolvedOutput solved) {
        return solved.typed() == null
                ? EndConditionResolver.resolve(condition, solved.legacy())
                : EndConditionResolver.resolve(condition, solved.typed(),
                        context.compiledSchema().endConditionSources());
    }

    private ValidationResponse validateCalculation(JsonNode input, String schemaId, String schemaVersion,
            CalculationResult calculation, Map<String, Double> params, BoundPhysicsModule typedModule) {
        if (calculation.typed() != null) {
            return validationService.validateTyped(input, schemaId, schemaVersion,
                    calculation.typed(), params, typedModule);
        }
        return validationService.validate(input, schemaId, schemaVersion,
                calculation.output(), params, typedModule);
    }

    private SolvedOutput solve(CalculationContext context, double horizon) {
        if (context.typedModule() == null) {
            SolverOutput legacy = context.legacySolver().solve(context.input(), context.params(), horizon, context.step());
            return new SolvedOutput(legacy, null);
        }
        var contract = context.compiledSchema().outputContract(OutputContractValidator.MAX_SAMPLES);
        if (contract == null) {
            throw new OutputContractException("Output contract is missing for schemaId="
                    + context.schemaId() + " schemaVersion=" + context.schemaVersion());
        }
        BoundPhysicsModule.SolvedOutput solved = context.typedModule().solve(contract,
                new SimulationClock(horizon, context.step()), context.compiledSchema().endConditionSources());
        return new SolvedOutput(solved.legacy(), solved.typed());
    }

    private void validateOutput(CalculationContext context, SolvedOutput solved) {
        if (solved.typed() != null) {
            OutputContractValidator.validate(context.compiledSchema(), solved.typed());
        } else {
            OutputContractValidator.validate(context.compiledSchema(), context.schemaDefinition(), solved.legacy());
        }
    }

    private RuntimePhysicsBinding bindRuntime(CompiledSchema compiledSchema, Map<String, Double> params,
                                               JsonNode input, SchemaDefinitionService.SolverBinding binding) {
        if (!physicsModuleRegistry.supportsPair(binding.numericalSolverId(), binding.referenceSolverId())) {
            boolean latestApproved = schemaDefinitions.isLatestApprovedEnabledVersion(
                    compiledSchema.schemaId(), compiledSchema.version());
            var pinned = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                    compiledSchema.schemaId(), compiledSchema.version(), binding.version(),
                    binding.numericalSolverId(), binding.referenceSolverId());
            var authorized = legacyPhysicsExecution.authorizeNumerical(
                    LegacyPhysicsExecutionAdapterV1.VERSION, pinned, latestApproved, solverRegistry);
            return new RuntimePhysicsBinding(null, authorized);
        }
        CanonicalQuantityBag quantities = canonicalQuantityCompiler.compile(
                compiledSchema, input, params);
        return new RuntimePhysicsBinding(
                physicsModuleRegistry.bind(binding.numericalSolverId(), binding.referenceSolverId(), quantities), null);
    }

    private EndConditionContract compileEndCondition(CompiledSchema compiledSchema, JsonNode input,
            double fallbackDuration, boolean typedRuntime) {
        try {
            if (typedRuntime) {
                return TypedEndConditionCompiler.compile(compiledSchema, input, fallbackDuration);
            }
            JsonNode normalized = EndConditionResolver.normalize(input, fallbackDuration);
            return LegacyEndConditionJsonAdapterV1.compile(normalized, fallbackDuration);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid end condition for schemaId=" + compiledSchema.schemaId()
                            + " schemaVersion=" + compiledSchema.version() + ": " + exception.getMessage());
        }
    }

    private SimulationResponse latestResponse(Simulation simulation, boolean includeRunId) {
        JsonNode latest = latestResult(simulation);
        boolean ready = simulation.getStatus() == SimulationStatus.READY
                || simulation.getStatus() == SimulationStatus.ARCHIVED;
        String status = simulation.getStatus() == null ? "UNKNOWN" : simulation.getStatus().name();
        UUID latestRunId = includeRunId
                ? simulationRunRepository.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())
                        .map(SimulationRun::getId).orElse(null)
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
        List<Double> time = list(result, "time");
        Map<String, Double> scalarOutputs = mapNumbers(result, "scalarOutputs");
        return new SimulationResponse(simulation.getId(), runId, simulation.getSpecification().getId(), simulation.getSchemaId(),
                ready, ready, time, map(result, "positions"), map(result, "velocities"),
                map(result, "accelerations"),
                LegacyScalarOutputSeriesAdapter.project(map(result, "values"), scalarOutputs, time.size()), scalarOutputs,
                scalarFields(result), parameters,
                safeVisualization(simulation.getSchemaId(), simulation.getSpecification().getSchemaVersion()),
                validationFromResult(result), result, resolvedEnd, 0, message);
    }

    private JsonNode latestResult(Simulation simulation) {
        return simulation.getLatestRun() == null ? null : simulation.getLatestRun().getResult();
    }

    /**
     * A replay must render the identity persisted with that run. It may read a
     * retired schema, but it must never silently reinterpret the snapshot as
     * the simulation's current/latest contract. Null metadata is accepted only
     * for rows written before V11 and remains a compatibility case.
     */
    private void assertRunIdentity(Simulation simulation, SimulationRun run) {
        Specification specification = simulation.getSpecification();
        requireMatchingIdentity(run.getSchemaId(), simulation.getSchemaId(), "schemaId");
        requireMatchingIdentity(run.getSchemaVersion(), specification == null ? null : specification.getSchemaVersion(),
                "schemaVersion");
        JsonNode result = run.getResult();
        if (result == null || !result.isObject()) return;
        requireMatchingIdentity(textOrNull(result, "schemaId"), run.getSchemaId(), "result.schemaId");
        requireMatchingIdentity(textOrNull(result, "schemaVersion"), run.getSchemaVersion(), "result.schemaVersion");
        requireMatchingIdentity(textOrNull(result, "bindingVersion"), run.getBindingVersion(), "result.bindingVersion");
        requireMatchingIdentity(textOrNull(result, "numericalSolverId"), run.getNumericalSolverId(),
                "result.numericalSolverId");
        requireMatchingIdentity(textOrNull(result, "referenceSolverId"), run.getReferenceSolverId(),
                "result.referenceSolverId");
        requireMatchingIdentity(textOrNull(result, "outputContractVersion"), run.getOutputContractVersion(),
                "result.outputContractVersion");
        requireMatchingIdentity(textOrNull(result, "outputContractChecksum"), run.getOutputContractChecksum(),
                "result.outputContractChecksum");
    }

    private static void requireMatchingIdentity(String actual, String expected, String field) {
        if (actual != null && expected != null && !actual.equals(expected)) {
            throw new ApiException(HttpStatus.CONFLICT, "Historical run snapshot identity mismatch for " + field);
        }
    }

    private static String textOrNull(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
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
                schemaDefinitions.requirePublishedVersion(schemaId, schemaVersion).getDefinition());
    }

    /** Returns null instead of throwing when the schema version is stale/unapproved.
     *  Used by history/get to prevent one old simulation from breaking the entire list. */
    private JsonNode safeVisualization(String schemaId, String schemaVersion) {
        try { return schemaDefinitions.visualization(schemaDefinitions.requireHistorical(schemaId, schemaVersion).getDefinition()); }
        catch (Exception ignored) { return null; }
    }

    private JsonNode resultJson(SolverOutput output, Map<String, Double> params, ValidationResponse validation,
                                ResolvedEnd resolvedEnd, CompiledSchema compiledSchema,
                                SchemaDefinitionService.SolverBinding binding) {
        ObjectNode node = objectMapper.createObjectNode();
        if (compiledSchema != null) {
            node.put("schemaId", compiledSchema.schemaId());
            node.put("schemaVersion", compiledSchema.version());
            node.put("outputContractVersion", compiledSchema.version());
            node.put("outputContractChecksum", compiledSchema.checksum());
        }
        if (binding != null) {
            node.put("bindingVersion", binding.version());
            node.put("numericalSolverId", binding.numericalSolverId());
            node.put("referenceSolverId", binding.referenceSolverId());
        }
        node.set("time", objectMapper.valueToTree(output.time()));
        node.set("positions", objectMapper.valueToTree(output.positions()));
        node.set("velocities", objectMapper.valueToTree(output.velocities()));
        node.set("accelerations", objectMapper.valueToTree(output.accelerations()));
        node.set("values", objectMapper.valueToTree(output.values()));
        node.set("scalarOutputs", objectMapper.valueToTree(output.scalarOutputs()));
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
