package com.example.backend.service;

import com.example.backend.dto.physics.ParameterAdjustmentRequest;
import com.example.backend.dto.physics.SimulationRequest;
import com.example.backend.dto.physics.SimulationResponse;
import com.example.backend.dto.physics.ValidationResponse;
import com.example.backend.entity.ConfirmationState;
import com.example.backend.entity.Simulation;
import com.example.backend.entity.SimulationRun;
import com.example.backend.entity.SimulationStatus;
import com.example.backend.entity.Specification;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.physics.PhysicsSolver;
import com.example.backend.physics.PhysicsSolverRegistry;
import com.example.backend.physics.SolverOutput;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.repository.SimulationRepository;
import com.example.backend.repository.SimulationRunRepository;
import com.example.backend.repository.SpecificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationService {
    private final SimulationRepository simulationRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final SpecificationRepository specificationRepository;
    private final PhysicsSolverRegistry solverRegistry;
    private final PhysicsValidationService validationService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final SchemaDefinitionService schemaDefinitions;
    private final SpecificationReadinessService readinessService;

    @Transactional
    public SimulationResponse run(SimulationRequest request) {
        User user = currentUserService.requireCurrentUser();
        Specification specification = requireOwnedSpecification(request.specificationId(), user);
        assertReady(specification);
        String schemaId = authoritativeSchema(request.schemaId(), specification);
        if (request.specification() != null && !request.specification().isNull()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Simulation input must use the persisted specification");
        }
        JsonNode input = readinessService.toJson(specification);
        Map<String, Double> params = request.adjustableParams() == null ? Map.of() : Map.copyOf(request.adjustableParams());
        if (request.durationSeconds() != null || request.stepSeconds() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Simulation timing is controlled by the persisted specification and schema contract");
        }
        SchemaVersion schema = schemaDefinitions.requireApproved(schemaId, specification.getSchemaVersion());
        assertAllowedAdjustments(schema, params);
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
        return calculateAndPersist(simulation, input, schemaId, specification.getSchemaVersion(), params, duration, step, "INITIAL");
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
        assertAllowedAdjustments(schema, request.adjustableParams());
        List<Double> previousTime = list(simulation.getLatestResult(), "time");
        if (previousTime.size() < 2) throw new ApiException(HttpStatus.CONFLICT, "Previous simulation timeline is unavailable");
        double duration = previousTime.get(previousTime.size() - 1);
        double step = previousTime.get(1) - previousTime.get(0);
        return calculateAndPersist(simulation, input, simulation.getSchemaId(),
                simulation.getSpecification().getSchemaVersion(), request.adjustableParams(), duration, step, "ADJUSTMENT");
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public SimulationResponse get(UUID id) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Simulation not found"));
        return latestResponse(simulation);
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public List<SimulationResponse> history() {
        User user = currentUserService.requireCurrentUser();
        return simulationRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(sim -> {
                    try { return latestResponse(sim); }
                    catch (Exception ignored) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private SimulationResponse calculateAndPersist(Simulation simulation, JsonNode input, String schemaId,
                                                   String schemaVersion,
                                                   Map<String, Double> params, double duration,
                                                   double step, String runType) {
        long started = System.nanoTime();
        PhysicsSolver solver = solverRegistry.get(
                schemaDefinitions.requireSolverBinding(schemaId, schemaVersion).numericalSolverId());
        SolverOutput output;
        try {
            output = solver.solve(input, params, duration, step);
        } catch (RuntimeException exception) {
            simulation.setStatus(SimulationStatus.FAILED);
            simulationRepository.save(simulation);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Simulation failed: " + exception.getMessage());
        }
        ValidationResponse validation = validationService.validate(
                input, schemaId, schemaVersion, output, params, simulation);
        JsonNode result = resultJson(output, params, validation);
        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);
        run.setRunType(runType);
        run.setValidationPassed(validation.passed());
        run.setDurationSeconds(output.time().isEmpty() ? 0 : output.time().get(output.time().size() - 1));
        run.setResult(result);
        simulationRunRepository.save(run);
        simulation.setLatestResult(result);
        simulation.setStatus(validation.passed() ? SimulationStatus.READY : SimulationStatus.BLOCKED);
        simulationRepository.save(simulation);
        specificationStatus(simulation.getSpecification(), validation, result);
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        return new SimulationResponse(simulation.getId(), run.getId(), simulation.getSpecification().getId(), schemaId,
                validation.passed(), validation.passed(), output.time(), output.positions(), output.velocities(),
                output.accelerations(), output.values(), params, visualization(schemaId, schemaVersion), validation, result, elapsed,
                validation.passed() ? "Validation passed" : "Simulation blocked because validation failed");
    }

    private SimulationResponse latestResponse(Simulation simulation) {
        JsonNode latest = simulation.getLatestResult();
        boolean ready = simulation.getStatus() == SimulationStatus.READY
                || simulation.getStatus() == SimulationStatus.ARCHIVED;
        String status = simulation.getStatus() == null ? "UNKNOWN" : simulation.getStatus().name();
        return new SimulationResponse(simulation.getId(), null, simulation.getSpecification().getId(), simulation.getSchemaId(),
                ready,
                ready,
                list(latest, "time"), map(latest, "positions"), map(latest, "velocities"),
                map(latest, "accelerations"), map(latest, "values"), mapNumbers(latest, "parameters"),
                safeVisualization(simulation.getSchemaId(), simulation.getSpecification().getSchemaVersion()),
                null, latest, 0, status);
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

    private String authoritativeSchema(String requested, Specification specification) {
        String persisted = specification.getSchemaId();
        schemaDefinitions.requireApproved(persisted, specification.getSchemaVersion());
        if (requested != null && !requested.isBlank() && !persisted.equals(requested)) {
            throw new ApiException(HttpStatus.CONFLICT, "Requested schema does not match the persisted specification");
        }
        return persisted;
    }

    private void assertAllowedAdjustments(SchemaVersion schema, Map<String, Double> params) {
        if (params == null || params.isEmpty()) return;
        var allowed = schemaDefinitions.adjustableKeys(schema.getDefinition());
        List<String> rejected = params.keySet().stream().filter(key -> !allowed.contains(key)).toList();
        if (!rejected.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,
                "Unsupported adjustable parameters: " + String.join(", ", rejected));
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

    private JsonNode resultJson(SolverOutput output, Map<String, Double> params, ValidationResponse validation) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("time", objectMapper.valueToTree(output.time()));
        node.set("positions", objectMapper.valueToTree(output.positions()));
        node.set("velocities", objectMapper.valueToTree(output.velocities()));
        node.set("accelerations", objectMapper.valueToTree(output.accelerations()));
        node.set("values", objectMapper.valueToTree(output.values()));
        node.set("parameters", objectMapper.valueToTree(params));
        node.set("validation", objectMapper.valueToTree(validation));
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
}
