package com.example.backend.ai.extraction;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import io.micrometer.core.instrument.MeterRegistry;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.prompt.ExtractionPromptBuilder;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.AiProviderProperties;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.entity.problem.SchemaVersion;
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
    private final SchemaDefinitionService schemaDefinitions;
    private final ExtractionPromptBuilder prompts;
    private final String model;
    private final String providerName;
    private final int maxAttempts;
    private final MeterRegistry meters;
    private final com.example.backend.simulation.assets.AssetSelectionService assetSelections;

    public StructuredExtractionProvider(ChatCompletionClient client, ObjectMapper objectMapper,
            SchemaDefinitionService schemaDefinitions,
            AiProviderProperties properties, JevProperties routingProperties,
            ResourceLoader resourceLoader, MeterRegistry meters,
            com.example.backend.simulation.assets.AssetSelectionService assetSelections) {
        this.client = client;
        this.objectMapper = objectMapper;
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
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException("Problem text must not be blank.");
        if (routingDecision == null) throw new IllegalArgumentException("Schema routing decision is required.");
        requireCurrentCandidates(routingDecision);
        List<CandidateContractProjection> candidates = routingDecision.extractionCandidates().stream()
                .map(SchemaCandidate::contract).toList();
        ExtractionPromptBuilder.PromptMessages messages = prompts.build(candidates, text.trim());
        ProviderExtractionResult result = complete(List.of(client.textMessage(SYSTEM_ROLE, messages.systemMessage()),
                client.textMessage("user", messages.userMessage())),
                "AI returned unreadable specification JSON.",
                "SPECIFICATION_EXTRACTION");
        return result;
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
                    Update the pinned spec using the answers and schema contract. Keep confirmed facts;
                    return one resolutionDecision per answered code. On refusal use DECLINE_SIMPLIFICATION;
                    apply only accepted reductions and list omitted IDs. Keep open questions; ask for required
                    values still missing and ask again only if an answer remains ambiguous. Use the user's language.

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
                client.textMessage("user", messages.userMessage())),
                "AI returned unreadable clarification JSON.", "SPECIFICATION_CLARIFICATION");
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
                Return one short question per finding, preserving its fieldPath.
                Write each question and option in the original problem's language, not English unless the original is English.
                Ask one concise clarification per finding in everyday physics language. Never expose
                schema names, field paths, quantity keys or other implementation terms in questions or options.
                If proposing one specific compatible change, ask whether the user agrees or refuses.

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
                    result.add(new AmbiguityItem("verification." + (++index), fieldPath, question, options));
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

    private ProviderExtractionResult complete(List<Map<String, Object>> messages,
            String invalidMessage, String step) {
        meters.summary("physlive.ai.prompt.characters").record(prompts.messageCharacterCount(messages));
        Exception lastFailure = null;
        List<Map<String, Object>> attemptMessages = messages;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            JsonNode draft = null;
            try {
                ChatCompletionClient.Completion completion = client.completeJson(model, attemptMessages);
                JsonNode json = client.parseJson(completion.content());
                draft = json;
                if (!json.isObject()) throw new IllegalStateException("AI response root must be a JSON object.");
                requireResponseShape(json);
                List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions = resolutionDecisions(json);
                JsonNode specificationJson = json.deepCopy();
                if (specificationJson instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                    object.remove("resolutionDecisions");
                }
                SpecificationDocument parsed = objectMapper.readerFor(SpecificationDocument.class)
                        .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .readValue(specificationJson);
                if (parsed == null) throw new IllegalStateException("AI response does not contain a specification.");
                return new ProviderExtractionResult(parsed, null, decisions);
            } catch (Exception failure) {
                lastFailure = failure;
                if (draft != null && draft.path("ambiguities").isArray()) {
                    List<String> paths = new ArrayList<>();
                    draft.path("ambiguities").forEach(item -> paths.add(item.path("fieldPath").asText("")));
                    log.warn("AI draft rejected: step={} attempt={}/{} reason={} ambiguityPaths={} objectCount={}",
                            step, attempt + 1, maxAttempts, failure.getMessage(), paths,
                            draft.path("objects").size());
                }
                if (isUpstreamHttpFailure(failure) || attempt + 1 >= maxAttempts) break;
                String detail = failure.getMessage() == null ? "Invalid structured response" : failure.getMessage();
                attemptMessages = new ArrayList<>(messages);
                attemptMessages.add(client.textMessage("user", "Revise the complete JSON using the supplied contract. "
                        + "Keep the original facts and unresolved questions. Previous response error: "
                        + detail.substring(0, Math.min(detail.length(), MAX_RETRY_ERROR_CHARACTERS))));
            }
        }
        String detail = lastFailure == null || lastFailure.getMessage() == null
                ? "Invalid structured response" : lastFailure.getMessage();
        String failureCode = isUpstreamHttpFailure(lastFailure)
                ? "AI_UPSTREAM_HTTP_ERROR" : "AI_STRUCTURED_RESPONSE_INVALID";
        log.warn("AI step failed: step={} code={} reason={}", step, failureCode,
                detail.substring(0, Math.min(detail.length(), MAX_RETRY_ERROR_CHARACTERS)));
        throw new AiStepException(step, failureCode, invalidMessage, lastFailure);
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

    private List<com.example.backend.ai.extraction.model.ResolutionDecision> resolutionDecisions(JsonNode response) {
        JsonNode values = response.path("resolutionDecisions");
        return objectMapper.convertValue(values, new com.fasterxml.jackson.core.type.TypeReference<>() { });
    }

    /** Checks only the wire format; the model owns the physics and clarification decisions. */
    private static void requireResponseShape(JsonNode response) {
        for (String field : List.of("contractVersion", "schemaId", "schemaVersion")) {
            if (!response.path(field).isTextual() || response.path(field).asText().isBlank()) {
                throw new IllegalArgumentException("SpecificationDocument." + field + " must be non-empty text");
            }
        }
        if (!response.path("confidence").isNumber()) {
            throw new IllegalArgumentException("SpecificationDocument.confidence must be a number");
        }
        for (String field : List.of("objects", "quantities", "relations", "ambiguities",
                "visualBindings", "resolutionDecisions")) {
            if (!response.path(field).isArray()) {
                throw new IllegalArgumentException("SpecificationDocument." + field + " must be an array");
            }
        }
        for (JsonNode item : response.path("objects")) {
            for (String field : List.of("id", "label", "type")) {
                if (!item.path(field).isTextual() || item.path(field).asText().isBlank()) {
                    throw new IllegalArgumentException("SpecificationDocument.objects[]." + field
                            + " must be non-empty text");
                }
            }
            if (!item.path("quantities").isArray()) {
                throw new IllegalArgumentException("SpecificationDocument.objects[].quantities must be an array");
            }
        }
        for (JsonNode item : response.path("ambiguities")) {
            for (String field : List.of("code", "fieldPath", "question")) {
                if (!item.path(field).isTextual() || item.path(field).asText().isBlank()) {
                    throw new IllegalArgumentException("SpecificationDocument.ambiguities[]." + field
                            + " must be non-empty text");
                }
            }
            if (!item.path("options").isArray()) {
                throw new IllegalArgumentException("SpecificationDocument.ambiguities[].options must be an array");
            }
        }
    }

    private void requireCurrentCandidates(SchemaRoutingDecision routingDecision) {
        for (SchemaCandidate candidate : routingDecision.candidates()) {
            schemaDefinitions.requireCurrentApproved(candidate.schemaId(), candidate.schemaVersion());
        }
    }

}
