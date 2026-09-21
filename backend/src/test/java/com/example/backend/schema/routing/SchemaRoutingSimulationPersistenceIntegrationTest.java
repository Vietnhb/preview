package com.example.backend.schema.routing;

import com.example.backend.ai.extraction.ExtractionCoordinator;
import com.example.backend.ai.extraction.ExtractionProvider;
import com.example.backend.ai.extraction.StrictSpecificationValidator;
import com.example.backend.ai.extraction.model.PhysicalQuantity;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.dto.simulation.SimulationRequest;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.curriculum.Topic;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.entity.simulation.SimulationRun;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.physics.binding.CanonicalQuantityCompiler;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.circuits.AcWaveformModule;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.repository.curriculum.TopicRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.example.backend.schema.routing.index.IndexedSchemaCandidate;
import com.example.backend.schema.routing.index.SchemaSearchIndex;
import com.example.backend.schema.routing.lexical.Bm25SchemaRetriever;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.service.SchemaRoutingService;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.EmbeddingResult;
import com.example.backend.schema.routing.vector.SchemaVectorRetriever;
import com.example.backend.schema.routing.verification.SchemaContractReranker;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.SpecificationReadinessService;
import com.example.backend.service.problem.SchemaCompiler;
import com.example.backend.service.simulation.PhysicsValidationService;
import com.example.backend.service.simulation.SimulationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/** Composes real routing, strict candidate extraction, typed execution and run snapshot creation. */
class SchemaRoutingSimulationPersistenceIntegrationTest {
    private static final String SCHEMA_ID = "ac_waveform";
    private static final String SCHEMA_VERSION = "1.1";
    private static final String TOPIC = "CIRCUITS";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void candidateBoundRawExtractionRunsThroughTypedRuntimeAndPersistsValidatedSnapshot() throws Exception {
        JsonNode source;
        try (var input = new ClassPathResource(
                "schemas/source/CIRCUITS/138__ac_waveform__1.1.json").getInputStream()) {
            source = mapper.readTree(input);
        }
        JsonNode definition = source.path("definition").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) definition).put("model", source.path("model").asText());
        String name = source.path("name").asText();

        CandidateContractProjection projection = CandidateContractProjection.from(
                SCHEMA_ID, SCHEMA_VERSION, TOPIC, name, definition);
        String searchText = searchText(projection);
        SchemaSearchDocument document = new SchemaSearchDocument(SCHEMA_ID, SCHEMA_VERSION, TOPIC, name,
                projection.modelId(), searchText, new SchemaCompiler(mapper).checksum(definition));
        IndexedSchemaCandidate candidate = new IndexedSchemaCandidate(document, projection);
        SchemaSearchIndex index = new SchemaSearchIndex();
        index.replace(List.of(candidate), new Bm25SchemaRetriever(1.2, 0.75).buildIndex(List.of(document)));

        EmbeddingClient embeddings = new EmbeddingClient() {
            @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(List.of(1.0, 0.0)); }
            @Override public String providerId() { return "integration-fake"; }
            @Override public String modelId() { return "fixture-v1"; }
            @Override public int dimension() { return 2; }
        };
        SchemaVectorRetriever vector = (query, provider, model, dimension, topK, eligible) -> {
            assertEquals("integration-fake", provider);
            assertEquals("fixture-v1", model);
            assertEquals(2, dimension);
            assertEquals(Set.of(new SchemaIdentity(SCHEMA_ID, SCHEMA_VERSION)), Set.copyOf(eligible));
            return List.of(new SchemaVectorRetriever.RankedVector(SCHEMA_ID, SCHEMA_VERSION, TOPIC, 1.0, 1));
        };
        SchemaRoutingProperties routingProperties = new SchemaRoutingProperties(true, 1, 1, 1, 60,
                0.55, 0.05, 20_000, 30_000, 64, 1.2, 0.75, 0.15,
                0.55, 0.25, 0.20, 0.75,
                new SchemaRoutingProperties.Embedding("integration-fake", "fixture-v1", 2,
                        Duration.ofSeconds(2)));
        UnitNormalizer unitNormalizer = new UnitNormalizer(mapper);
        SchemaRoutingService routing = new SchemaRoutingService(index, embeddings, vector,
                new SchemaContractReranker(unitNormalizer, routingProperties), routingProperties);
        CandidateBoundFakeProvider fakeProvider = new CandidateBoundFakeProvider(mapper, unitNormalizer);
        ExtractionCoordinator extractionCoordinator = new ExtractionCoordinator(fakeProvider, routing);

        var extraction = extractionCoordinator.extract(
                "An AC source has peak voltage 12 V, frequency 0.25 Hz and phase 90 degrees.");
        assertEquals(SchemaRoutingDecision.Status.SELECTED, extraction.routingDecision().status());
        assertEquals(List.of(SCHEMA_ID + "@" + SCHEMA_VERSION), extraction.routingDecision().candidates().stream()
                .map(item -> item.schemaId() + "@" + item.schemaVersion()).toList());
        assertEquals(List.of("peak_voltage", "frequency", "phase"), extraction.document().quantities().stream()
                .map(PhysicalQuantity::name).toList());
        PhysicalQuantity canonicalPhase = extraction.document().quantities().getLast();
        assertEquals(new BigDecimal("90"), canonicalPhase.value());
        assertEquals("deg", canonicalPhase.originalUnit());
        assertEquals("rad", canonicalPhase.normalizedUnit());
        assertEquals(Math.PI / 2.0, canonicalPhase.normalizedValue().doubleValue(), 1e-14);

        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(SCHEMA_ID);
        schema.setVersion(SCHEMA_VERSION);
        schema.setName(name);
        schema.setTopic(TOPIC);
        schema.setLifecycleStatus(LifecycleStatus.APPROVED);
        schema.setDefinition(definition);
        SolverVersion binding = new SolverVersion();
        binding.setSchemaId(SCHEMA_ID);
        binding.setVersion(SCHEMA_VERSION);
        binding.setSolverId(AcWaveformModule.NUMERICAL_SOLVER_ID);
        binding.setLifecycleStatus(LifecycleStatus.APPROVED);
        binding.setOutputDefinition(mapper.readTree("""
                {"referenceSolverId":"ac_waveform_reference"}
                """));

        SchemaVersionRepository schemaVersions = mock(SchemaVersionRepository.class);
        SolverVersionRepository solverVersions = mock(SolverVersionRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        when(schemaVersions.findFirstBySchemaIdAndVersion(SCHEMA_ID, SCHEMA_VERSION)).thenReturn(Optional.of(schema));
        when(solverVersions.findFirstBySchemaIdAndVersion(SCHEMA_ID, SCHEMA_VERSION)).thenReturn(Optional.of(binding));
        Topic enabledTopic = new Topic();
        enabledTopic.setName(TOPIC);
        enabledTopic.setEnabled(true);
        when(topics.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(enabledTopic));
        when(schemaVersions.findAllBySchemaIdIgnoreCaseAndLifecycleStatusAndTopicInOrderByCreatedAtDesc(
                SCHEMA_ID, LifecycleStatus.APPROVED, List.of(TOPIC))).thenReturn(List.of(schema));
        when(topics.existsByNameIgnoreCaseAndEnabledTrue(TOPIC)).thenReturn(true);
        SchemaDefinitionService definitions = new SchemaDefinitionService(schemaVersions, solverVersions, topics);
        SpecificationReadinessService readiness = new SpecificationReadinessService(definitions, mapper);

        UUID specificationId = UUID.randomUUID();
        Specification specification = new Specification();
        specification.setId(specificationId);
        specification.setSchemaId(SCHEMA_ID);
        specification.setSchemaVersion(SCHEMA_VERSION);
        specification.setContractVersion(extraction.document().contractVersion());
        specification.setTopic(TOPIC);
        specification.setConfidence(extraction.document().confidence());
        specification.setObjects(mapper.valueToTree(extraction.document().objects()));
        specification.setQuantities(mapper.valueToTree(extraction.document().quantities()));
        specification.setRelations(mapper.valueToTree(extraction.document().relations()));
        specification.setEndCondition(extraction.document().endCondition());
        specification.setAmbiguity(mapper.valueToTree(extraction.document().ambiguities()));
        specification.setConfirmationState(ConfirmationState.NO_AMBIGUITY);

        UUID runId = UUID.randomUUID();
        UUID simulationId = UUID.randomUUID();
        User user = new User();
        SimulationRepository simulations = mock(SimulationRepository.class);
        LibraryItemRepository libraries = mock(LibraryItemRepository.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SpecificationRepository specifications = mock(SpecificationRepository.class);
        PhysicsSolverRegistry legacySolvers = mock(PhysicsSolverRegistry.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(specifications.findByIdAndSubmissionOwner(specificationId, user)).thenReturn(Optional.of(specification));
        when(simulations.save(any(Simulation.class))).thenAnswer(invocation -> {
            Simulation saved = invocation.getArgument(0);
            saved.setId(simulationId);
            return saved;
        });
        when(runs.save(any(SimulationRun.class))).thenAnswer(invocation -> {
            SimulationRun saved = invocation.getArgument(0);
            saved.setId(runId);
            return saved;
        });
        when(specifications.save(any(Specification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var adapter = new LegacyPhysicsExecutionAdapterV1(List.of());
        var validation = new PhysicsValidationService(new ReferenceSolverRegistry(List.of()), definitions, adapter);
        SimulationService simulationsService = new SimulationService(simulations, libraries, runs, specifications,
                legacySolvers, validation, currentUser, mapper, definitions, readiness,
                new CanonicalQuantityCompiler(unitNormalizer),
                new PhysicsModuleRegistry(List.of(new AcWaveformModule())), adapter);
        var response = simulationsService.run(new SimulationRequest(specificationId, Map.of()));

        assertTrue(response.success());
        assertTrue(response.validationPassed());
        assertEquals(simulationId, response.simulationId());
        assertEquals(runId, response.simulationRunId());
        assertEquals(201, response.time().size());
        assertEquals(12.0, response.values().get("voltage").getFirst(), 1e-12);
        assertEquals(12.0 / Math.sqrt(2.0), response.values().get("rmsVoltage").getLast(), 1e-12);
        assertEquals(8, response.validation().checkpoints().size());
        assertEquals("phase", specification.getQuantities().get(2).path("name").asText());
        assertEquals("deg", specification.getQuantities().get(2).path("originalUnit").asText());

        ArgumentCaptor<SimulationRun> runCaptor = ArgumentCaptor.forClass(SimulationRun.class);
        verify(runs).save(runCaptor.capture());
        SimulationRun persisted = runCaptor.getValue();
        assertNotNull(persisted.getResult());
        assertTrue(persisted.isValidationPassed());
        assertEquals("INITIAL", persisted.getRunType());
        assertEquals(0.1, persisted.getDurationSeconds(), 1e-12);
        assertTrue(persisted.getResult().path("validation").path("passed").asBoolean());
        assertEquals(12.0, persisted.getResult().path("values").path("voltage").get(0).asDouble(), 1e-12);
        assertEquals(0.25, persisted.getResult().path("parameters").path("frequency").asDouble(), 1e-12);
        assertEquals("time_limit", persisted.getResult().path("resolvedEnd").path("reason").asText());
        verify(simulations, times(2)).save(any(Simulation.class));
        verify(legacySolvers, never()).get(any());
    }

    private static String searchText(CandidateContractProjection contract) {
        List<String> terms = new ArrayList<>(List.of(contract.schemaId(), contract.schemaVersion(), contract.topic(),
                contract.name(), contract.modelId()));
        for (CandidateContractProjection.QuantityProjection quantity : contract.requiredQuantities()) {
            terms.add(quantity.key());
            terms.addAll(quantity.aliases());
            terms.addAll(quantity.symbols());
            terms.addAll(quantity.acceptedInputUnits());
        }
        for (CandidateContractProjection.QuantityProjection quantity : contract.optionalQuantities()) {
            terms.add(quantity.key());
            terms.addAll(quantity.aliases());
            terms.addAll(quantity.symbols());
            terms.addAll(quantity.acceptedInputUnits());
        }
        return String.join(" ", terms);
    }

    /** Fake transport that still exercises the production strict JSON and candidate membership validators. */
    private static final class CandidateBoundFakeProvider implements ExtractionProvider {
        private final ObjectMapper mapper;
        private final UnitNormalizer units;

        private CandidateBoundFakeProvider(ObjectMapper mapper, UnitNormalizer units) {
            this.mapper = mapper;
            this.units = units;
        }

        @Override public String providerName() { return "candidate-bound-fake"; }
        @Override public String modelVersion() { return "fixture-v1"; }
        @Override public ExtractionPath path() { return ExtractionPath.OPENROUTER; }
        @Override public boolean isAvailable() { return true; }

        @Override
        public ProviderExtractionResult extract(String text) {
            throw new IllegalStateException("The routed schema candidate is required");
        }

        @Override
        public ProviderExtractionResult extract(String text, SchemaRoutingDecision decision) {
            try {
                JsonNode raw = mapper.readTree("""
                        {
                          "contractVersion":"1.0",
                          "schemaVersion":"1.0",
                          "topic":"CIRCUITS",
                          "schemaId":"ac_waveform",
                          "objects":[],
                          "quantities":[
                            {"name":"V0","symbol":"V0","value":12,"originalUnit":"V","confidence":1,"sourceText":"12 V"},
                            {"name":"f","symbol":"f","value":0.25,"originalUnit":"Hz","confidence":1,"sourceText":"0.25 Hz"},
                            {"name":"phi","symbol":"phi","value":90,"originalUnit":"deg","confidence":1,"sourceText":"90 degrees"}
                          ],
                          "relations":[],
                          "endCondition":{"type":"time_limit","duration":0.1},
                          "confidence":0.99,
                          "ambiguities":[]
                        }
                        """);
                ((com.fasterxml.jackson.databind.node.ObjectNode) raw).put("schemaVersion", SCHEMA_VERSION);
                StrictSpecificationValidator.validate(raw);
                var selected = StrictSpecificationValidator.validateCandidateMembership(raw, decision);
                if (decision.status() != SchemaRoutingDecision.Status.SELECTED) {
                    throw new IllegalArgumentException("The fake extractor requires a selected candidate");
                }
                SpecificationDocument parsed = mapper.treeToValue(raw, SpecificationDocument.class);
                List<PhysicalQuantity> canonical = parsed.quantities().stream()
                        .map(quantity -> canonicalize(quantity, selected.contract()))
                        .toList();
                SpecificationDocument document = new SpecificationDocument(selected.schemaVersion(),
                        selected.topic(), selected.schemaId(), parsed.objects(), canonical, parsed.relations(),
                        parsed.endCondition(), parsed.confidence(), parsed.ambiguities(), parsed.contractVersion());
                return new ProviderExtractionResult(document, null);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot build fake provider specification", exception);
            }
        }

        private PhysicalQuantity canonicalize(PhysicalQuantity input,
                CandidateContractProjection contract) {
            List<CandidateContractProjection.QuantityProjection> matches = new ArrayList<>();
            for (var quantity : contract.requiredQuantities()) if (accepts(quantity, input.name())) matches.add(quantity);
            for (var quantity : contract.optionalQuantities()) if (accepts(quantity, input.name())) matches.add(quantity);
            if (matches.size() != 1) throw new IllegalArgumentException("Fake input alias is not uniquely declared");

            var normalized = units.normalize(input.value(), input.originalUnit());
            boolean acceptedUnit = normalized.knownUnit() && matches.getFirst().acceptedInputUnits().stream()
                    .map(unit -> units.normalize(BigDecimal.ONE, unit))
                    .anyMatch(allowed -> allowed.knownUnit()
                            && allowed.normalizedUnit().equals(normalized.normalizedUnit()));
            if (!acceptedUnit) throw new IllegalArgumentException("Fake raw unit is outside the candidate contract");
            return new PhysicalQuantity(matches.getFirst().key(), input.symbol(), input.value(), input.originalUnit(),
                    normalized.normalizedValue(), normalized.normalizedUnit(), input.confidence(), input.sourceText());
        }

        private boolean accepts(CandidateContractProjection.QuantityProjection quantity, String inputName) {
            return quantity.key().equals(inputName) || quantity.aliases().contains(inputName)
                    || quantity.symbols().contains(inputName);
        }

        @Override
        public ProviderExtractionResult resolveAmbiguities(String originalText, JsonNode currentSpecification,
                Map<String, String> answers) {
            throw new UnsupportedOperationException("The integration fixture does not resolve ambiguities");
        }
    }
}
