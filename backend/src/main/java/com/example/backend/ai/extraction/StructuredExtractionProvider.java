package com.example.backend.ai.extraction;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import io.micrometer.core.instrument.MeterRegistry;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.prompt.ExtractionPromptBuilder;
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
    private static final String AMBIGUITIES = "ambiguities";
    private static final String SCHEMA_ID = "schemaId";
    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String RESOLUTION_DECISIONS = "resolutionDecisions";
    private static final String OBJECTS = "objects";
    private static final String CONTRACT_VERSION = "contractVersion";
    private static final List<String> CONTRACT_FIELDS = List.of(CONTRACT_VERSION, SCHEMA_ID, SCHEMA_VERSION);
    private static final List<String> ARRAY_FIELDS = List.of(OBJECTS, "quantities", "relations", AMBIGUITIES,
            RESOLUTION_DECISIONS);
    private static final List<String> OBJECT_FIELDS = List.of("id", "label", "type");
    private static final List<String> AMBIGUITY_FIELDS = List.of("code", "fieldPath", "question");
    private static final String SCHEMA_GENERATION_RETRY = "The provider rejected the generated JSON. Regenerate the entire response as valid JSON with balanced braces and brackets, no extra closing delimiters, and no duplicate property names. Match the supplied response schema and include every required top-level property, including empty arrays. Do not add empty items, quoted JSON objects, or partial objects to arrays. Preserve facts and ask about unresolved details.";
    private static final int MAX_RETRY_ERROR_CHARACTERS = 500;

    private final ChatCompletionClient client;
    private final ObjectMapper objectMapper;
    private final SchemaDefinitionService schemaDefinitions;
    private final ExtractionPromptBuilder prompts;
    private final String model;
    private final String providerName;
    private final int maxAttempts;
    private final MeterRegistry meters;

    public StructuredExtractionProvider(ChatCompletionClient client, ObjectMapper objectMapper,
            SchemaDefinitionService schemaDefinitions,
            AiProviderProperties properties, JevProperties routingProperties,
            ResourceLoader resourceLoader, MeterRegistry meters) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.schemaDefinitions = schemaDefinitions;
        this.model = properties.textModel();
        this.providerName = properties.name();
        this.maxAttempts = properties.maxAttempts();
        this.meters = meters;
        String baseSystemPrompt;
        try (var input = resourceLoader.getResource(properties.systemPromptResource()).getInputStream()) {
            baseSystemPrompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the configured AI system prompt", exception);
        }
        this.prompts = new ExtractionPromptBuilder(baseSystemPrompt, routingProperties.maximumPromptCharacters());
    }

    @Override public String providerName() { return providerName; }
    @Override public String modelVersion() { return model; }
    @Override public ExtractionPath path() { return ExtractionPath.AI_PROVIDER; }
    @Override public boolean isAvailable() { return client.isAvailable() && StringUtils.hasText(model); }

    @Override
    public ProviderExtractionResult extract(String text, SchemaRoutingDecision routingDecision) {
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException("Problem text must not be blank.");
        if (routingDecision == null) throw new IllegalArgumentException("Schema routing decision is required.");
        requireCurrentCandidates(routingDecision);
        List<CandidateContractProjection> candidates = routingDecision.extractionCandidates().stream()
                .map(SchemaCandidate::contract).toList();
        String routingAssessment = "reasonCode=" + routingDecision.reasonCode()
                + ", confidence=" + routingDecision.confidence() + ", margin=" + routingDecision.margin()
                + ", status=" + routingDecision.status();
        ExtractionPromptBuilder.PromptMessages messages = prompts.build(candidates, text.trim(), routingAssessment);
        return complete(List.of(client.textMessage(SYSTEM_ROLE, messages.systemMessage()),
                client.textMessage("user", messages.userMessage())),
                "AI returned unreadable specification JSON.",
                "SPECIFICATION_EXTRACTION", response -> {
                    var selected = routingDecision.extractionCandidates().stream()
                            .filter(candidate -> candidate.schemaId().equals(response.path(SCHEMA_ID).asText())
                                    && candidate.schemaVersion().equals(response.path(SCHEMA_VERSION).asText()))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                    "Response identity must match one of the routed capability contracts"));
                    requirePinnedIdentity(response, selected.schemaId(), selected.schemaVersion(), selected.topic());
                    if (response.path(AMBIGUITIES).isEmpty()) {
                        SchemaVersion selectedPack = schemaDefinitions.requireCurrentApproved(
                                selected.schemaId(), selected.schemaVersion());
                        schemaDefinitions.validateSpecificationReferences(response,
                                selectedPack.getDefinition(), selected.schemaId());
                    }
                }, routingDecision.extractionCandidates().stream()
                        .flatMap(candidate -> candidate.contract().endConditionCapabilities().stream()).distinct().toList());
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
        String schemaId = currentSpecification.path(SCHEMA_ID).asText("");
        String schemaVersion = currentSpecification.path(SCHEMA_VERSION).asText("");
        SchemaVersion pinned = schemaDefinitions.requirePublishedVersion(schemaId, schemaVersion);
        CandidateContractProjection contract = CandidateContractProjection.from(pinned.getSchemaId(), pinned.getVersion(),
                pinned.getTopic(), pinned.getName(), schemaDefinitions.projectCoreTypes(pinned.getDefinition()));
        String request = buildAmbiguityResolutionRequest(originalText, currentSpecification, answers, conversation);
        ExtractionPromptBuilder.PromptMessages messages = prompts.build(List.of(contract), request);
        return complete(List.of(client.textMessage(SYSTEM_ROLE, messages.systemMessage()),
                client.textMessage("user", messages.userMessage())),
                "AI returned unreadable clarification JSON.", "SPECIFICATION_CLARIFICATION",
                response -> validateAmbiguityResolution(response, answers, schemaId, schemaVersion, pinned),
                contract.endConditionCapabilities());
    }

    private String buildAmbiguityResolutionRequest(String originalText, JsonNode currentSpecification,
            Map<String, String> answers, List<ConversationTurn> conversation) {
        try {
            return """
                    Update the pinned spec using the answers and schema contract. Keep confirmed facts;
                    return one resolutionDecision per answered code. A declined proposal is not a request to stop;
                    apply only accepted changes and list omitted IDs. Return at most one remaining question,
                    choosing the most consequential uncertainty after considering all unanswered details. Ask for required
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
    }

    private void validateAmbiguityResolution(JsonNode response, Map<String, String> answers,
            String schemaId, String schemaVersion, SchemaVersion pinned) {
        Set<String> codes = new HashSet<>();
        for (JsonNode decision : response.path(RESOLUTION_DECISIONS)) {
            String code = decision.path("code").asText();
            if (!answers.containsKey(code) || !codes.add(code)) {
                throw new IllegalArgumentException("Return exactly one decision per submitted answer code");
            }
            if ("UNRESOLVED".equals(decision.path("outcome").asText()) && !retainsQuestion(response, code)) {
                throw new IllegalArgumentException("An UNRESOLVED decision must retain its question code");
            }
        }
        if (!codes.equals(answers.keySet())) {
            throw new IllegalArgumentException("Every submitted answer requires a resolutionDecision");
        }
        requirePinnedIdentity(response, schemaId, schemaVersion, pinned.getTopic());
        if (response.path(AMBIGUITIES).isEmpty()) {
            schemaDefinitions.validateSpecificationReferences(response, pinned.getDefinition(), schemaId);
        }
    }

    private static boolean retainsQuestion(JsonNode response, String code) {
        for (JsonNode question : response.path(AMBIGUITIES)) {
            if (code.equals(question.path("code").asText())) return true;
        }
        return false;
    }

    private static void requirePinnedIdentity(JsonNode response, String schemaId, String version, String topic) {
        if (!schemaId.equals(response.path(SCHEMA_ID).asText())
                || !version.equals(response.path(SCHEMA_VERSION).asText())
                || !topic.equals(response.path("topic").asText()))
            throw new IllegalArgumentException("Response identity must match the routed capability contract");
    }

    private ProviderExtractionResult complete(List<Map<String, Object>> messages,
            String invalidMessage, String step, Consumer<JsonNode> contractCheck,
            List<String> endConditionCapabilities) {
        meters.summary("physlive.ai.prompt.characters").record(prompts.messageCharacterCount(messages));
        Exception lastFailure = null;
        List<Map<String, Object>> attemptMessages = messages;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return completeAttempt(attemptMessages, endConditionCapabilities, contractCheck);
            } catch (Exception failure) {
                lastFailure = failure;
                boolean schemaGenerationFailure = isRetryableSchemaGenerationFailure(failure);
                if (shouldStop(failure, schemaGenerationFailure, attempt)) break;
                attemptMessages = retryMessages(messages, schemaGenerationFailure, failure);
            }
        }
        boolean schemaGenerationFailure = isRetryableSchemaGenerationFailure(lastFailure);
        String detail = finalFailureDetail(lastFailure, schemaGenerationFailure);
        String failureCode = failureCode(lastFailure, schemaGenerationFailure);
        String truncatedDetail = truncate(detail);
        if (log.isWarnEnabled()) {
            log.warn("AI step failed: step={} code={} reason={}", step, failureCode, truncatedDetail);
        }
        throw new AiStepException(step, failureCode, invalidMessage, lastFailure);
    }

    private ProviderExtractionResult completeAttempt(List<Map<String, Object>> messages,
            List<String> endConditionCapabilities, Consumer<JsonNode> contractCheck) {
        ChatCompletionClient.Completion completion = client.completeStructured(model, messages, endConditionCapabilities);
        JsonNode json = client.parseJson(completion.content());
        if (!json.isObject()) throw new IllegalStateException("AI response root must be a JSON object.");
        requireResponseShape(json);
        contractCheck.accept(json);
        List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions = resolutionDecisions(json);
        JsonNode specificationJson = json.deepCopy();
        if (specificationJson instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.remove(RESOLUTION_DECISIONS);
        }
        SpecificationDocument parsed;
        try {
            parsed = objectMapper.readerFor(SpecificationDocument.class)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(specificationJson);
        } catch (IOException exception) {
            throw new IllegalStateException("AI response could not be mapped to a specification.", exception);
        }
        if (parsed == null) throw new IllegalStateException("AI response does not contain a specification.");
        return new ProviderExtractionResult(parsed, null, decisions);
    }

    private boolean shouldStop(Exception failure, boolean schemaGenerationFailure, int attempt) {
        return (!schemaGenerationFailure && isUpstreamHttpFailure(failure)) || attempt + 1 >= maxAttempts;
    }

    private List<Map<String, Object>> retryMessages(List<Map<String, Object>> messages,
            boolean schemaGenerationFailure, Exception failure) {
        String detail = schemaGenerationFailure ? SCHEMA_GENERATION_RETRY : failureDetail(failure);
        List<Map<String, Object>> retry = new ArrayList<>(messages);
        retry.add(client.textMessage("user", "Revise the complete JSON using the supplied contract. "
                + "Keep the original facts and unresolved questions. Previous response error: "
                + truncate(detail)));
        return retry;
    }

    private static String failureDetail(Exception failure) {
        return failure.getMessage() == null ? "Invalid structured response" : failure.getMessage();
    }

    private static String finalFailureDetail(Exception failure, boolean schemaGenerationFailure) {
        if (schemaGenerationFailure) return "Provider rejected generated JSON against the required response schema.";
        return failure == null ? "Invalid structured response" : failureDetail(failure);
    }

    private String failureCode(Exception failure, boolean schemaGenerationFailure) {
        return !schemaGenerationFailure && failure != null && isUpstreamHttpFailure(failure)
                ? "AI_UPSTREAM_HTTP_ERROR" : "AI_STRUCTURED_RESPONSE_INVALID";
    }

    private static String truncate(String detail) {
        return detail.substring(0, Math.min(detail.length(), MAX_RETRY_ERROR_CHARACTERS));
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

    private boolean isRetryableSchemaGenerationFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.springframework.web.client.HttpClientErrorException http
                    && http.getStatusCode().value() == 400) {
                try {
                    return "json_validate_failed".equals(objectMapper.readTree(http.getResponseBodyAsString())
                            .path("error").path("code").asText());
                } catch (Exception ignored) {
                    return false;
                }
            }
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return false;
    }

    private List<com.example.backend.ai.extraction.model.ResolutionDecision> resolutionDecisions(JsonNode response) {
        JsonNode values = response.path(RESOLUTION_DECISIONS);
        return objectMapper.convertValue(values, new com.fasterxml.jackson.core.type.TypeReference<>() { });
    }

    /** Checks only the wire format; the model owns the physics and clarification decisions. */
    private static void requireResponseShape(JsonNode response) {
        requireTextFields(response);
        requireConfidence(response);
        requireArrayFields(response);
        requireSingleAmbiguity(response);
        requireObjectFields(response);
        requireAmbiguityItems(response);
    }

    private static void requireTextFields(JsonNode response) {
        for (String field : CONTRACT_FIELDS) {
            if (!response.path(field).isTextual() || response.path(field).asText().isBlank()) {
                throw new IllegalArgumentException("SpecificationDocument." + field + " must be non-empty text");
            }
        }
    }

    private static void requireConfidence(JsonNode response) {
        if (!response.path("confidence").isNumber()) {
            throw new IllegalArgumentException("SpecificationDocument.confidence must be a number");
        }
    }

    private static void requireArrayFields(JsonNode response) {
        for (String field : ARRAY_FIELDS) {
            if (!response.path(field).isArray()) {
                throw new IllegalArgumentException("SpecificationDocument." + field + " must be an array");
            }
        }
    }

    private static void requireSingleAmbiguity(JsonNode response) {
        if (response.path(AMBIGUITIES).size() > 1) {
            throw new IllegalArgumentException("Return only the highest-priority clarification question for this turn");
        }
    }

    private static void requireObjectFields(JsonNode response) {
        for (JsonNode item : response.path(OBJECTS)) {
            for (String field : OBJECT_FIELDS) {
                if (!item.path(field).isTextual() || item.path(field).asText().isBlank()) {
                    throw new IllegalArgumentException("SpecificationDocument.objects[]." + field
                            + " must be non-empty text");
                }
            }
        }
    }

    private static void requireAmbiguityItems(JsonNode response) {
        Set<String> questionCodes = new HashSet<>();
        for (JsonNode item : response.path(AMBIGUITIES)) {
            if (!questionCodes.add(item.path("code").asText())) {
                throw new IllegalArgumentException("Ambiguity codes must be unique");
            }
            for (String field : AMBIGUITY_FIELDS) {
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
