package com.example.backend.ai.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import io.micrometer.core.instrument.MeterRegistry;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.ai.extraction.model.PhysicalObject;
import com.example.backend.ai.extraction.model.PhysicalQuantity;
import com.example.backend.ai.extraction.model.QuantityContractViolation;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection.EntityTypeProjection;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection.QuantityProjection;
import com.example.backend.ai.extraction.prompt.ExtractionPromptBuilder;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.AiProviderProperties;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public final class StructuredExtractionProvider implements ExtractionProvider {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StructuredExtractionProvider.class);
    private static final String SYSTEM_ROLE = "system";
    private static final int MAX_RETRY_ERROR_CHARACTERS = 500;

    private final ChatCompletionClient client;
    private final ObjectMapper objectMapper;
    private final UnitNormalizer unitNormalizer;
    private final SchemaDefinitionService schemaDefinitions;
    private final ExtractionPromptBuilder prompts;
    private final String model;
    private final String providerName;
    private final int maxAttempts;
    private final MeterRegistry meters;
    private final com.example.backend.simulation.assets.AssetSelectionService assetSelections;

    public StructuredExtractionProvider(ChatCompletionClient client, ObjectMapper objectMapper,
            UnitNormalizer unitNormalizer, SchemaDefinitionService schemaDefinitions,
            AiProviderProperties properties, JevProperties routingProperties,
            ResourceLoader resourceLoader, MeterRegistry meters,
            com.example.backend.simulation.assets.AssetSelectionService assetSelections) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.unitNormalizer = unitNormalizer;
        this.schemaDefinitions = schemaDefinitions;
        this.model = properties.textModel();
        this.providerName = properties.name();
        this.maxAttempts = properties.maxAttempts();
        this.meters = meters;
        this.assetSelections = assetSelections;
        String baseSystemPrompt;
        try (var input = resourceLoader.getResource(properties.systemPromptResource()).getInputStream()) {
            baseSystemPrompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the configured AI system prompt", exception);
        }
        this.prompts = new ExtractionPromptBuilder(baseSystemPrompt, routingProperties.candidateTopK(),
                routingProperties.maximumPromptCharacters());
    }

    @Override public String providerName() { return providerName; }
    @Override public String modelVersion() { return model; }
    @Override public ExtractionPath path() { return ExtractionPath.AI_PROVIDER; }
    @Override public boolean isAvailable() { return client.isAvailable() && StringUtils.hasText(model); }

    @Override
    public ProviderExtractionResult extract(String text) {
        throw new IllegalStateException("A pinned schema routing decision is required before AI extraction.");
    }

    @Override
    public ProviderExtractionResult extract(String text, SchemaRoutingDecision routingDecision) {
        return extract(text, routingDecision, List.of());
    }

    @Override
    public ProviderExtractionResult extract(String text, SchemaRoutingDecision routingDecision,
            List<String> verificationFindings) {
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException("Problem text must not be blank.");
        if (routingDecision == null) throw new IllegalArgumentException("Schema routing decision is required.");
        requireCurrentCandidates(routingDecision);
        List<CandidateContractProjection> candidates = routingDecision.extractionCandidates().stream()
                .map(SchemaCandidate::contract).toList();
        ExtractionPromptBuilder.PromptMessages messages = prompts.build(candidates, text.trim(), verificationFindings);
        ProviderExtractionResult result = complete(List.of(client.textMessage(SYSTEM_ROLE, messages.systemMessage()),
                client.textMessage("user", messages.userMessage())), routingDecision,
                "AI response does not match the strict candidate extraction contract.", null,
                "SPECIFICATION_EXTRACTION");
        return rejectCapacityRepairViolations(result, verificationFindings);
    }

    @Override
    public ProviderExtractionResult resolveAmbiguities(String originalText, JsonNode currentSpecification,
            Map<String, String> answers) {
        return resolveAmbiguities(originalText, currentSpecification, answers, List.of());
    }

    @Override
    public ProviderExtractionResult resolveAmbiguities(String originalText, JsonNode currentSpecification,
            Map<String, String> answers, List<ConversationTurn> conversation) {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException("Ambiguity answers are required.");
        }
        if (currentSpecification == null || !currentSpecification.isObject()) {
            throw new IllegalArgumentException("Pinned specification is required for ambiguity resolution.");
        }
        String schemaId = currentSpecification.path("schemaId").asText("");
        String schemaVersion = currentSpecification.path("schemaVersion").asText("");
        SchemaVersion pinned = schemaDefinitions.requireCurrentApproved(schemaId, schemaVersion);
        CandidateContractProjection contract = CandidateContractProjection.from(pinned.getSchemaId(), pinned.getVersion(),
                pinned.getTopic(), pinned.getName(), pinned.getDefinition());
        String request;
        try {
            request = """
                    Update the pinned spec from the answers. Keep confirmed facts and unresolved questions.
                    Return one resolutionDecisions entry per answered code. For accepted capacity reduction,
                    omit equivalent objects if their identity makes no difference to the user's request;
                    otherwise ask which object to retain. List every omitted object ID in the decision.
                    Leave missing required values unset; the backend asks for them after compatibility is resolved.
                    Phrase any question in the language of the original problem.

                    Original problem:
                    %s

                    Current specification:
                    %s

                    Teacher answers keyed by ambiguity code:
                    %s

                    Conversation history, ordered oldest to newest:
                    %s

                    """.formatted(originalText, objectMapper.writeValueAsString(currentSpecification),
                    objectMapper.writeValueAsString(answers),
                    objectMapper.writeValueAsString(conversation == null ? List.of() : conversation));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare ambiguity resolution request.", exception);
        }
        ExtractionPromptBuilder.PromptMessages messages = prompts.build(List.of(contract), request);
        SchemaCandidate candidate = new SchemaCandidate(contract, List.of("PINNED_SCHEMA_VERSION"), 1);
        SchemaRoutingDecision decision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "PINNED_FOR_AMBIGUITY_RESOLUTION", List.of(candidate), 1, 1);
        return complete(List.of(client.textMessage(SYSTEM_ROLE, messages.systemMessage()),
                client.textMessage("user", messages.userMessage())), decision,
                "AI ambiguity response does not match the pinned schema contract.",
                currentSpecification, "SPECIFICATION_CLARIFICATION");
    }

    /**
     * Produces only visual bindings. The confirmed specification is copied by
     * the backend and is never round-tripped through the model during this step.
     */
    @Override
    public ProviderExtractionResult bindVisualAssets(String originalText, JsonNode currentSpecification,
            SchemaRoutingDecision assetRoute, List<ConversationTurn> conversation) {
        if (currentSpecification == null || !currentSpecification.isObject()) {
            throw new IllegalArgumentException("A confirmed specification is required for visual binding.");
        }
        if (assetRoute == null || assetRoute.assets() == null) {
            throw new IllegalArgumentException("A classified catalog route is required for visual binding.");
        }

        String request;
        try {
            request = """
                    Return only visualBindings: one per renderer target, using supplied asset IDs and
                    existing object IDs. Match target kind; use EXACT for a faithful depiction,
                    SUBSTITUTE with a concrete Vietnamese visualDifference for an approximation,
                    or UNSUPPORTED with a reason. Only decorative props may be OMITTED.
                    The confirmed physics spec is read-only.

                    Original user description:
                    %s

                    Confirmed specification (read-only):
                    %s

                    Classified catalog options and renderer targets:
                    %s

                    Conversation:
                    %s
                    """.formatted(originalText == null ? "" : originalText,
                    objectMapper.writeValueAsString(currentSpecification),
                    objectMapper.writeValueAsString(assetSelections.promptContext(assetRoute)),
                    objectMapper.writeValueAsString(conversation == null ? List.of() : conversation));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare the visual binding request.", exception);
        }

        List<Map<String, Object>> messages = List.of(client.textMessage("user", request));
        RuntimeException failure = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                ChatCompletionClient.Completion completion = client.completeVisualBindings(model, messages);
                JsonNode response = client.parseJson(completion.content());
                JsonNode merged = overlayVisualBindings(objectMapper, currentSpecification, response);
                boolean pinnedSchema = assetRoute.candidates().stream().anyMatch(candidate ->
                        candidate.schemaId().equals(merged.path("schemaId").asText())
                                && candidate.schemaVersion().equals(merged.path("schemaVersion").asText()));
                if (!pinnedSchema) throw new IllegalArgumentException(
                        "Visual binding route does not match the confirmed schema version.");
                SpecificationDocument document = objectMapper.treeToValue(merged, SpecificationDocument.class);
                return new ProviderExtractionResult(document, completion.rawResponse(), List.of());
            } catch (RuntimeException exception) {
                failure = exception;
            } catch (Exception exception) {
                failure = new IllegalStateException(exception);
            }
            if (attempt + 1 < maxAttempts) {
                String detail = failure == null || failure.getMessage() == null
                        ? "invalid visual binding response" : failure.getMessage();
                messages = List.of(client.textMessage("user", request + "\n\nPrevious output was rejected: "
                        + detail.substring(0, Math.min(detail.length(), MAX_RETRY_ERROR_CHARACTERS))
                        + ". Return the full visualBindings object again."));
            }
        }
        throw new IllegalStateException("AI visual binding response did not match the binding contract.", failure);
    }

    @Override
    public JsonNode summarizeAssetRequests(String originalText, JsonNode confirmedSpecification) {
        if (confirmedSpecification == null || !confirmedSpecification.isObject()) {
            throw new IllegalArgumentException("A confirmed specification is required before asset classification.");
        }
        String request;
        try {
            request = """
                    Return only assetRequests: one per renderer target, with its targetId, existing
                    objectId, kind, and visual details stated by the user. If appearance is unknown,
                    say so. Do not choose asset IDs or change physics.

                    Original user description:
                    %s

                    Confirmed specification and renderer targets (read-only):
                    %s
                    """.formatted(originalText == null ? "" : originalText,
                    objectMapper.writeValueAsString(confirmedSpecification));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare the asset request summary.", exception);
        }

        try {
            var completion = client.completeAssetRequestSummary(model,
                    List.of(client.textMessage("user", request)));
            JsonNode response = client.parseJson(completion.content());
            return validateAssetRequestSummary(confirmedSpecification, response);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("AI asset request summary did not match the renderer contract.", failure);
        }
    }

    static JsonNode validateAssetRequestSummary(JsonNode confirmedSpecification, JsonNode summary) {
        if (confirmedSpecification == null || !confirmedSpecification.isObject()
                || summary == null || !summary.isObject() || summary.size() != 1
                || !summary.path("assetRequests").isArray()) {
            throw new IllegalArgumentException("Summary must contain only an assetRequests array.");
        }
        java.util.Map<String, JsonNode> expectedTargets = new java.util.LinkedHashMap<>();
        confirmedSpecification.path("visualTargets").forEach(target -> {
            String targetId = target.path("targetId").asText();
            if (targetId.isBlank() || expectedTargets.putIfAbsent(targetId, target) != null) {
                throw new IllegalArgumentException("Renderer target IDs must be unique and non-empty.");
            }
        });
        java.util.Set<String> objectIds = new java.util.HashSet<>();
        confirmedSpecification.path("objects").forEach(object -> {
            String objectId = object.path("id").asText();
            if (objectId.isBlank() || !objectIds.add(objectId)) {
                throw new IllegalArgumentException("Confirmed object IDs must be unique and non-empty.");
            }
        });
        java.util.Set<String> seenTargets = new java.util.HashSet<>();
        java.util.Set<String> representedObjects = new java.util.HashSet<>();
        java.util.Set<String> actorObjects = new java.util.HashSet<>();
        for (JsonNode item : summary.path("assetRequests")) {
            if (!item.isObject() || item.size() != 4
                    || !item.path("targetId").isTextual()
                    || !item.path("objectId").isTextual()
                    || !item.path("kind").isTextual()
                    || !item.path("requestedDescription").isTextual()
                    || item.path("requestedDescription").asText().isBlank()) {
                throw new IllegalArgumentException("Every asset request needs a target, object, kind, and description.");
            }
            String targetId = item.path("targetId").asText();
            String objectId = item.path("objectId").asText();
            JsonNode target = expectedTargets.get(targetId);
            if (target == null || !seenTargets.add(targetId)
                    || !target.path("kind").asText().equals(item.path("kind").asText())
                    || !objectIds.contains(objectId)) {
                throw new IllegalArgumentException("Summary contains a duplicate or unpinned renderer/object identity.");
            }
            representedObjects.add(objectId);
            if ("actor".equals(item.path("kind").asText()) && !actorObjects.add(objectId)) {
                throw new IllegalArgumentException("Actor targets must bind distinct confirmed object IDs.");
            }
        }
        if (expectedTargets.isEmpty() || !seenTargets.equals(expectedTargets.keySet())
                || !representedObjects.containsAll(objectIds)) {
            throw new IllegalArgumentException("Summary must cover all renderer targets and confirmed objects.");
        }
        return summary.deepCopy();
    }

    static JsonNode overlayVisualBindings(ObjectMapper mapper, JsonNode currentSpecification, JsonNode response) {
        if (currentSpecification == null || !currentSpecification.isObject()
                || response == null || !response.isObject()
                || !response.path("visualBindings").isArray()
                || response.size() != 1 || !response.has("visualBindings")) {
            throw new IllegalArgumentException("AI response must contain only a visualBindings array.");
        }
        java.util.Set<String> objectIds = new java.util.HashSet<>();
        currentSpecification.path("objects").forEach(object -> objectIds.add(object.path("id").asText()));
        java.util.Set<String> targetIds = new java.util.HashSet<>();
        for (JsonNode binding : response.path("visualBindings")) {
            if (!binding.isObject() || binding.size() != 5
                    || !binding.path("targetId").isTextual()
                    || !binding.path("entityId").isTextual() && !binding.path("entityId").isNull()
                    || !binding.path("assetId").isTextual() && !binding.path("assetId").isNull()
                    || !binding.path("visualDifference").isTextual()
                            && !binding.path("visualDifference").isNull()) {
                throw new IllegalArgumentException("A visual binding has invalid fields or types.");
            }
            String targetId = binding.path("targetId").asText();
            String entityId = binding.path("entityId").asText("");
            String assetId = binding.path("assetId").asText("");
            String match = binding.path("match").asText();
            String difference = binding.path("visualDifference").asText("");
            if (targetId.isBlank() || !targetIds.add(targetId)
                    || !java.util.Set.of("EXACT", "SUBSTITUTE", "UNSUPPORTED", "OMITTED").contains(match)) {
                throw new IllegalArgumentException("A visual binding has an invalid or duplicate target/match.");
            }
            boolean omitted = "OMITTED".equals(match);
            if (omitted) {
                if (!entityId.isBlank() || !assetId.isBlank()) {
                    throw new IllegalArgumentException("An omitted visual target cannot reference an object or asset.");
                }
            } else if (entityId.isBlank() || !objectIds.contains(entityId)) {
                throw new IllegalArgumentException("Visual binding must reference an existing objectId.");
            }
            if (assetId.isBlank() && !omitted && !"UNSUPPORTED".equals(match)
                    || !assetId.isBlank() && (omitted || "UNSUPPORTED".equals(match))) {
                throw new IllegalArgumentException("Visual binding match must agree with its catalog asset.");
            }
            if (("SUBSTITUTE".equals(match) || "UNSUPPORTED".equals(match)) && difference.isBlank()) {
                throw new IllegalArgumentException("A substitute or unsupported visual needs an explanation.");
            }
        }
        JsonNode merged = currentSpecification.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) merged)
                .set("visualBindings", response.path("visualBindings").deepCopy());
        return merged;
    }

    @Override
    public List<AmbiguityItem> phraseVerificationQuestions(String originalText, List<String> findings) {
        return phraseVerificationQuestions(originalText, findings, List.of());
    }

    @Override
    public List<AmbiguityItem> phraseVerificationQuestions(String originalText, List<String> findings,
            List<ConversationTurn> conversation) {
        if (!StringUtils.hasText(originalText) || findings == null || findings.isEmpty()) return List.of();
        String prompt = """
                Return one short question per finding in the user's language, preserving its fieldPath.
                Ask for a decision, not facts already given. For capacity, explain the mismatch and
                proposed reduction, with explicit accept and refuse/revise options. Do not imply
                any object was already removed or expose machine keys.

                Original problem:
                %s

                Conversation history:
                %s

                Contract findings:
                %s
                """.formatted(originalText.trim(), objectMapper.valueToTree(conversation == null
                        ? List.of() : conversation).toString(), String.join("\n", findings));
        RuntimeException failure = null;
        String attemptPrompt = prompt;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                var completion = client.completeAmbiguityQuestions(model,
                        List.of(client.textMessage("user", attemptPrompt)));
                JsonNode root = client.parseJson(completion.content());
                List<AmbiguityItem> result = new ArrayList<>();
                int index = 0;
                for (JsonNode item : root.path("questions")) {
                    String fieldPath = item.path("fieldPath").asText("").trim();
                    String question = item.path("question").asText("").trim();
                    if (!StringUtils.hasText(fieldPath) || !StringUtils.hasText(question)
                            || question.contains("issue=") || question.contains("fieldPath=")
                            || question.contains("actorCapacity=")) {
                        throw new IllegalStateException("AI returned an invalid clarification question.");
                    }
                    List<String> options = new ArrayList<>();
                    item.path("options").forEach(option -> options.add(option.asText()));
                    boolean capacity = CompatibilityFieldPaths.isCapacity(fieldPath)
                            && findings.stream().anyMatch(this::isCapacityFinding);
                    result.add(new AmbiguityItem((capacity ? "jev.capacity." : "jev.verification.")
                            + (++index), fieldPath, question, options));
                }
                if (result.isEmpty()) throw new IllegalStateException("AI returned no clarification questions.");
                return List.copyOf(result);
            } catch (RuntimeException exception) {
                failure = exception;
                attemptPrompt = prompt + "\nThe previous output was invalid: " + exception.getMessage()
                        + " Regenerate the complete JSON response.";
            }
        }
        throw new IllegalStateException("AI could not phrase the required clarification questions.", failure);
    }

    private ProviderExtractionResult rejectCapacityRepairViolations(ProviderExtractionResult result,
            List<String> verificationFindings) {
        if (verificationFindings == null || verificationFindings.stream().noneMatch(this::isCapacityFinding)) {
            return result;
        }
        int baselineObjects = verificationFindings.stream()
                .mapToInt(this::knownObjects)
                .max().orElse(-1);
        boolean lostObjects = baselineObjects >= 0 && result.document().objects().size() < baselineObjects;
        if (lostObjects) {
            throw new IllegalStateException(
                    "Capacity verification findings forbid reducing the explicitly extracted objects.");
        }
        return result;
    }

    private boolean isCapacityFinding(String finding) {
        return finding != null && (finding.contains("VISUAL_CAPACITY_EXCEEDED")
                || finding.contains("JEV_CAPACITY_EXCEEDED"));
    }

    private int knownObjects(String finding) {
        if (finding == null) return -1;
        for (String part : finding.split(";")) {
            String value = part.trim();
            for (String key : List.of("knownObjects=", "routedCount=", "expectedObjects=")) {
                if (!value.startsWith(key)) continue;
                try { return Integer.parseInt(value.substring(key.length())); }
                catch (NumberFormatException ignored) { return -1; }
            }
        }
        return -1;
    }

    private ProviderExtractionResult complete(List<Map<String, Object>> messages, SchemaRoutingDecision routingDecision,
            String invalidMessage, JsonNode currentSpecification, String step) {
        meters.summary("physlive.ai.prompt.characters").record(prompts.messageCharacterCount(messages));
        try {
                ChatCompletionClient.Completion completion = client.completeStructured(model, messages);
                JsonNode json = client.parseJson(completion.content());
                if (!json.isObject()) throw new IllegalStateException("AI response root must be a JSON object.");
                StrictSpecificationValidator.validate(json);
                rejectMachineFindingsInQuestions(json);
                List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions = resolutionDecisions(json);
                boolean simplificationAccepted = hasAcceptedSimplification(decisions, currentSpecification);
                preserveVisualIdentity(json, currentSpecification, simplificationAccepted);
                SchemaCandidate candidate = StrictSpecificationValidator.validateCandidateMembership(json, routingDecision,
                        unitNormalizer, true,
                        capacityDecisionPending(currentSpecification, decisions));
                SchemaVersion pinned = schemaDefinitions.requireCurrentApproved(candidate.schemaId(), candidate.schemaVersion());
                JsonNode specificationJson = json.deepCopy();
                if (specificationJson instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                    object.remove("resolutionDecisions");
                }
                SpecificationDocument parsed = objectMapper.treeToValue(specificationJson, SpecificationDocument.class);
                if (parsed == null) throw new IllegalStateException("AI response does not contain a specification.");
                SpecificationDocument normalized = normalize(parsed, pinned);
                // Asset binding is a post-extraction JEV gate concern. Keeping
                // extraction free of selection side effects lets the coordinator
                // verify objects/schema first and only then build a plan.
                return new ProviderExtractionResult(normalized, null, decisions);
        } catch (QuantityContractViolation violation) {
            throw violation;
        } catch (Exception failure) {
            String detail = failure.getMessage() == null ? "Invalid structured response" : failure.getMessage();
            String failureCode = isUpstreamHttpFailure(failure)
                    ? "AI_UPSTREAM_HTTP_ERROR" : "AI_STRUCTURED_RESPONSE_INVALID";
            log.warn("AI step failed: step={} code={} reason={}", step, failureCode,
                    detail.substring(0, Math.min(detail.length(), MAX_RETRY_ERROR_CHARACTERS)));
            throw new AiStepException(step, failureCode, invalidMessage, failure);
        }
    }

    public static JsonNode preserveConfirmedPhysics(ObjectMapper mapper, JsonNode current, JsonNode updated,
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions) {
        if (current == null || !current.isObject() || updated == null || !updated.isObject()) {
            throw new IllegalArgumentException("Current and updated specifications must be JSON objects.");
        }
        java.util.Map<String, com.example.backend.ai.extraction.model.ResolutionDecision> decisionsByCode =
                new java.util.HashMap<>();
        if (decisions != null) decisions.forEach(decision -> decisionsByCode.put(decision.code(), decision));
        java.util.Set<String> answeredPaths = new java.util.HashSet<>();
        current.path("ambiguities").forEach(ambiguity -> {
            var decision = decisionsByCode.get(ambiguity.path("code").asText());
            if (decision != null && decision.outcome()
                    != com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.UNRESOLVED) {
                answeredPaths.add(ambiguity.path("fieldPath").asText());
            }
        });

        var merged = (com.fasterxml.jackson.databind.node.ObjectNode) updated.deepCopy();
        java.util.Set<String> currentOpenPaths = new java.util.HashSet<>();
        current.path("ambiguities").forEach(item -> currentOpenPaths.add(item.path("fieldPath").asText()));
        java.util.Set<String> preservedQuantityPaths = new java.util.HashSet<>();
        restoreQuantities(current.path("quantities"), merged.withArray("quantities"),
                "quantities", answeredPaths, preservedQuantityPaths);
        JsonNode currentObjects = current.path("objects");
        var updatedObjects = merged.withArray("objects");
        for (JsonNode currentObject : currentObjects) {
            String objectId = currentObject.path("id").asText();
            JsonNode updatedObject = null;
            for (JsonNode candidate : updatedObjects) {
                if (objectId.equals(candidate.path("id").asText())) {
                    updatedObject = candidate;
                    break;
                }
            }
            if (updatedObject instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                restoreQuantities(currentObject.path("quantities"), object.withArray("quantities"),
                        "objects." + objectId + ".quantities", answeredPaths, preservedQuantityPaths);
            }
        }
        var retainedAmbiguities = mapper.createArrayNode();
        updated.path("ambiguities").forEach(ambiguity -> {
            String fieldPath = ambiguity.path("fieldPath").asText();
            boolean contradictedByConfirmedValue = preservedQuantityPaths.contains(fieldPath)
                    && !currentOpenPaths.contains(fieldPath) && !answeredPaths.contains(fieldPath);
            if (!contradictedByConfirmedValue) retainedAmbiguities.add(ambiguity.deepCopy());
        });
        merged.set("ambiguities", retainedAmbiguities);
        if (!answeredPaths.stream().anyMatch(path -> path.equals("relations") || path.startsWith("relations."))) {
            merged.set("relations", current.path("relations").deepCopy());
        }
        if (!answeredPaths.stream().anyMatch(path -> path.equals("endCondition")
                || path.startsWith("endCondition."))) {
            merged.set("endCondition", current.path("endCondition").deepCopy());
        }
        return merged;
    }

    private static void restoreQuantities(JsonNode existing,
            com.fasterxml.jackson.databind.node.ArrayNode target, String prefix,
            java.util.Set<String> answeredPaths, java.util.Set<String> preservedPaths) {
        if (!existing.isArray()) return;
        for (JsonNode quantity : existing) {
            String name = quantity.path("name").asText("");
            if (name.isBlank()) continue;
            String fieldPath = prefix + "." + name;
            if (answeredPaths.contains(fieldPath)) continue;
            preservedPaths.add(fieldPath);
            int index = -1;
            for (int i = 0; i < target.size(); i++) {
                JsonNode value = target.get(i);
                if (name.equals(value.path("name").asText(""))) {
                    index = i;
                    break;
                }
            }
            JsonNode preserved = quantity.deepCopy();
            if (index < 0) target.add(preserved);
            else target.set(index, preserved);
        }
    }

    private void rejectMachineFindingsInQuestions(JsonNode document) {
        for (JsonNode ambiguity : document.path("ambiguities")) {
            String question = ambiguity.path("question").asText("");
            if (question.contains("issue=") || question.contains("fieldPath=")
                    || question.contains("actorCapacity=")) {
                throw new IllegalStateException(
                        "Ambiguity questions must be natural user-facing language and must not expose machine findings.");
            }
        }
    }

    private boolean isUpstreamHttpFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.springframework.web.client.HttpStatusCodeException) return true;
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return false;
    }

    private boolean capacityDecisionPending(JsonNode current,
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions) {
        if (current == null || !current.path("ambiguities").isArray()) return false;
        for (var ambiguity : current.path("ambiguities")) {
            String path = ambiguity.path("fieldPath").asText();
            if (!CompatibilityFieldPaths.isCapacity(path)) continue;
            var answer = decisions.stream().filter(item -> item.code().equals(ambiguity.path("code").asText()))
                    .findFirst().orElse(null);
            if (CompatibilityFieldPaths.CAPACITY.equals(path)) return true;
            if (answer == null || answer.outcome()
                    != com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ANSWERED) return true;
        }
        return false;
    }

    private List<com.example.backend.ai.extraction.model.ResolutionDecision> resolutionDecisions(JsonNode response) {
        JsonNode values = response.path("resolutionDecisions");
        if (!values.isArray()) return List.of();
        return objectMapper.convertValue(values, new com.fasterxml.jackson.core.type.TypeReference<>() { });
    }

    private boolean hasAcceptedSimplification(
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions, JsonNode current) {
        if (current == null || decisions == null || decisions.isEmpty()) return false;
        java.util.Map<String, String> pathsByCode = new java.util.HashMap<>();
        current.path("ambiguities").forEach(item -> pathsByCode.put(item.path("code").asText(),
                item.path("fieldPath").asText()));
        return decisions.stream().anyMatch(decision -> {
            String path = pathsByCode.get(decision.code());
            return decision.outcome() == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ACCEPT_SIMPLIFICATION
                    || (CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT.equals(path)
                    && decision.outcome() == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ANSWERED);
        });
    }

    private void preserveVisualIdentity(JsonNode response, JsonNode current, boolean simplificationAccepted) {
        if (current == null) return;
        java.util.Set<JsonNode> before = new java.util.HashSet<>();
        java.util.Set<JsonNode> after = new java.util.HashSet<>();
        current.path("visualBindings").forEach(before::add);
        response.path("visualBindings").forEach(after::add);
        if (!before.equals(after) || (!simplificationAccepted
                && !objectIdentities(current).equals(objectIdentities(response)))) {
            throw new IllegalStateException("Preserve existing visualBindings and object id/label/type exactly; only update answered physics quantities.");
        }
        if (simplificationAccepted && !objectIdentities(current).containsAll(objectIdentities(response))) {
            throw new IllegalStateException("An accepted simplification may retain only existing object identities.");
        }
    }

    private java.util.Set<List<String>> objectIdentities(JsonNode document) {
        java.util.Set<List<String>> result = new java.util.HashSet<>();
        document.path("objects").forEach(object -> result.add(List.of(object.path("id").asText(),
                object.path("label").asText(), object.path("type").asText())));
        return result;
    }

    private void requireCurrentCandidates(SchemaRoutingDecision routingDecision) {
        for (SchemaCandidate candidate : routingDecision.candidates()) {
            schemaDefinitions.requireCurrentApproved(candidate.schemaId(), candidate.schemaVersion());
        }
    }

    private SpecificationDocument normalize(SpecificationDocument document, SchemaVersion schema) {
        if (!schema.getVersion().equals(document.schemaVersion())) {
            throw new IllegalStateException("AI schema version does not match the pinned candidate version.");
        }
        List<PhysicalQuantity> quantities = new ArrayList<>();
        for (PhysicalQuantity quantity : document.quantities()) {
            if (!StringUtils.hasText(quantity.name()) || quantity.value() == null
                    || !StringUtils.hasText(quantity.originalUnit())) {
                throw new IllegalStateException("AI returned an incomplete physical quantity.");
            }
            String canonicalName = schemaDefinitions.canonicalQuantityKey(schema.getDefinition(), quantity.name());
            if (!StringUtils.hasText(canonicalName)) throw new IllegalStateException("AI quantity has no canonical schema key.");
            JsonNode quantityDefinition = findQuantityDefinition(schema.getDefinition(), canonicalName);
            List<String> expectedUnits = textValues(quantityDefinition == null ? null : quantityDefinition.path("allowedUnits"));
            UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(quantity.value(), quantity.originalUnit());
            String fieldPath = "quantities." + canonicalName;
            if (!normalized.knownUnit()) {
                throw new QuantityContractViolation(fieldPath, quantity.value(), quantity.originalUnit(), expectedUnits,
                        "The supplied unit is not recognized.");
            }
            if (quantityDefinition == null || !allowsUnit(quantityDefinition.path("allowedUnits"), normalized.normalizedUnit())) {
                throw new QuantityContractViolation(fieldPath, quantity.value(), quantity.originalUnit(), expectedUnits,
                        "The supplied unit does not match this schema input.");
            }
            validatePhysicalDomain(quantityDefinition, normalized.normalizedValue(), canonicalName, fieldPath,
                    quantity.originalUnit(), expectedUnits);
            quantities.add(new PhysicalQuantity(canonicalName, quantity.symbol(), quantity.value(), quantity.originalUnit(),
                    normalized.normalizedValue(), normalized.normalizedUnit(), clamp(quantity.confidence()), quantity.sourceText()));
        }

        CandidateContractProjection contract = CandidateContractProjection.from(schema.getSchemaId(),
                schema.getVersion(), schema.getTopic(), schema.getName(), schema.getDefinition());
        List<PhysicalObject> objects = new ArrayList<>();
        for (PhysicalObject object : document.objects()) {
            EntityTypeProjection entity = contract.entityTypes().stream()
                    .filter(item -> item.type().equals(object.type())).findFirst().orElse(null);
            if (entity == null) {
                if (!object.quantities().isEmpty()) {
                    throw new IllegalStateException("AI returned entity-local quantities outside the selected entity contract.");
                }
                objects.add(object);
                continue;
            }
            List<PhysicalQuantity> local = normalizeEntityQuantities(object.id(), object.quantities(), entity);
            objects.add(new PhysicalObject(object.id(), object.label(), object.type(), local));
        }

        JsonNode endCondition = defaultTimeLimitWhenOmitted(document.endCondition(), schema);
        if (endCondition == null || endCondition.isNull()) throw new IllegalStateException("AI response is missing endCondition.");
        List<String> errors = EndConditionResolver.validateNode(endCondition,
                schema.getDefinition().path("execution").path("durationSeconds").asDouble());
        if (!errors.isEmpty()) throw new IllegalStateException("AI returned an invalid endCondition: " + String.join("; ", errors));

        return new SpecificationDocument(schema.getVersion(), schema.getTopic(),
                schema.getSchemaId(), objects, quantities, document.relations(), endCondition,
                clamp(document.confidence()), document.ambiguities(), SpecificationDocument.CURRENT_SCHEMA_VERSION,
                document.visualBindings());
    }

    /**
     * A time limit without a teacher-provided duration uses the approved
     * schema execution horizon. This is catalog configuration, not an AI
     * inference. Explicit non-positive values remain invalid and are retried.
     */
    private JsonNode defaultTimeLimitWhenOmitted(JsonNode source, SchemaVersion schema) {
        if (!(source instanceof com.fasterxml.jackson.databind.node.ObjectNode object)) return source;
        if (!"time_limit".equalsIgnoreCase(object.path("type").asText())) return source;
        JsonNode duration = object.get("duration");
        if (duration != null && !duration.isNull()) return source;
        double fallback = schema.getDefinition().path("execution").path("durationSeconds").asDouble(Double.NaN);
        if (!Double.isFinite(fallback) || fallback <= 0) return source;
        var normalized = object.deepCopy();
        normalized.put("duration", fallback);
        return normalized;
    }

    private List<PhysicalQuantity> normalizeEntityQuantities(String objectId, List<PhysicalQuantity> source,
            EntityTypeProjection entity) {
        List<PhysicalQuantity> result = new ArrayList<>();
        for (PhysicalQuantity quantity : source) {
            if (!StringUtils.hasText(quantity.name()) || quantity.value() == null
                    || !StringUtils.hasText(quantity.originalUnit())) {
                throw new IllegalStateException("AI returned an incomplete entity physical quantity.");
            }
            String canonicalName = canonicalQuantityName(entity.requiredQuantities(), entity.optionalQuantities(), quantity.name());
            QuantityProjection projection = quantityProjection(entity, canonicalName);
            if (!StringUtils.hasText(canonicalName) || projection == null) {
                throw new IllegalStateException("AI entity quantity has no canonical contract key.");
            }
            UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(quantity.value(), quantity.originalUnit());
            String fieldPath = "objects." + objectId + ".quantities." + canonicalName;
            if (!normalized.knownUnit() || !allowsUnit(projection.acceptedInputUnits(), normalized.normalizedUnit())) {
                throw new QuantityContractViolation(fieldPath, quantity.value(), quantity.originalUnit(),
                        projection.acceptedInputUnits(), "The supplied unit does not match this entity input.");
            }
            validatePhysicalDomain(projection.constraints(), normalized.normalizedValue(), canonicalName, fieldPath,
                    quantity.originalUnit(), projection.acceptedInputUnits());
            result.add(new PhysicalQuantity(canonicalName, quantity.symbol(), quantity.value(), quantity.originalUnit(),
                    normalized.normalizedValue(), normalized.normalizedUnit(), clamp(quantity.confidence()), quantity.sourceText()));
        }
        return List.copyOf(result);
    }

    private QuantityProjection quantityProjection(EntityTypeProjection entity, String key) {
        return java.util.stream.Stream.concat(entity.requiredQuantities().stream(), entity.optionalQuantities().stream())
                .filter(item -> item.key().equals(key)).findFirst().orElse(null);
    }

    private String canonicalQuantityName(List<QuantityProjection> required,
            List<QuantityProjection> optional, String suppliedName) {
        java.util.Set<String> matches = new java.util.HashSet<>();
        java.util.stream.Stream.concat(required.stream(), optional.stream()).forEach(item -> {
            if (item.key().equals(suppliedName) || item.aliases().contains(suppliedName)
                    || item.symbols().contains(suppliedName)) matches.add(item.key());
        });
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    private JsonNode findQuantityDefinition(JsonNode definition, String key) {
        for (String group : List.of("requiredQuantities", "optionalQuantities")) {
            for (JsonNode quantity : definition.path(group)) if (key.equals(quantity.path("key").asText())) return quantity;
        }
        return null;
    }

    private boolean allowsUnit(JsonNode allowedUnits, String normalizedUnit) {
        for (JsonNode allowed : allowedUnits) {
            UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(BigDecimal.ONE, allowed.asText());
            String canonical = normalized.knownUnit() ? normalized.normalizedUnit() : allowed.asText();
            if (canonical.equals(normalizedUnit)) return true;
        }
        return false;
    }

    private boolean allowsUnit(List<String> allowedUnits, String normalizedUnit) {
        for (String allowed : allowedUnits) {
            UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(BigDecimal.ONE, allowed);
            String canonical = normalized.knownUnit() ? normalized.normalizedUnit() : allowed;
            if (canonical.equals(normalizedUnit)) return true;
        }
        return false;
    }

    private void validatePhysicalDomain(JsonNode definition, BigDecimal value, String key, String fieldPath,
            String suppliedUnit, List<String> expectedUnits) {
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric) || (definition.path("positive").asBoolean(false) && numeric <= 0)
                || (definition.path("nonNegative").asBoolean(false) && numeric < 0)
                || (definition.path("integer").asBoolean(false) && value.stripTrailingZeros().scale() > 0)
                || (definition.path("min").isNumber() && numeric < definition.path("min").asDouble())
                || (definition.path("max").isNumber() && numeric > definition.path("max").asDouble())) {
            throw new QuantityContractViolation(fieldPath, value, suppliedUnit, expectedUnits,
                    "The supplied value is outside the schema's allowed domain for " + key + ".");
        }
    }

    private void validatePhysicalDomain(Map<String, Object> constraints, BigDecimal value, String key,
            String fieldPath, String suppliedUnit, List<String> expectedUnits) {
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric)
                || bool(constraints.get("positive")) && numeric <= 0
                || bool(constraints.get("nonNegative")) && numeric < 0
                || bool(constraints.get("integer")) && value.stripTrailingZeros().scale() > 0
                || number(constraints.get("min"), constraints.get("minimum"), constraints.get("minInclusive")) != null
                    && numeric < number(constraints.get("min"), constraints.get("minimum"), constraints.get("minInclusive"))
                || number(constraints.get("max"), constraints.get("maximum"), constraints.get("maxInclusive")) != null
                    && numeric > number(constraints.get("max"), constraints.get("maximum"), constraints.get("maxInclusive"))) {
            throw new QuantityContractViolation(fieldPath, value, suppliedUnit, expectedUnits,
                    "The supplied value is outside the schema's allowed domain for " + key + ".");
        }
    }

    private List<String> textValues(JsonNode array) {
        if (array == null || !array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        array.forEach(item -> { if (item.isTextual()) values.add(item.asText()); });
        return List.copyOf(values);
    }

    private boolean bool(Object value) { return value instanceof Boolean booleanValue && booleanValue; }

    private Double number(Object... values) {
        for (Object value : values) if (value instanceof Number number) return number.doubleValue();
        return null;
    }

    private BigDecimal clamp(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
