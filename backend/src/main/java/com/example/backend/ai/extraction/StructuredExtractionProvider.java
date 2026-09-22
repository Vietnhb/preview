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
import com.example.backend.ai.extraction.model.PhysicalQuantity;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.prompt.ExtractionPromptBuilder;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.AiProviderProperties;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.model.SchemaSelectionScore;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public final class StructuredExtractionProvider implements ExtractionProvider {
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

    public StructuredExtractionProvider(ChatCompletionClient client, ObjectMapper objectMapper,
            UnitNormalizer unitNormalizer, SchemaDefinitionService schemaDefinitions,
            AiProviderProperties properties, JevProperties routingProperties,
            ResourceLoader resourceLoader, MeterRegistry meters) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.unitNormalizer = unitNormalizer;
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
        List<CandidateContractProjection> candidates = routingDecision.candidates().stream()
                .map(SchemaCandidate::contract).toList();
        ExtractionPromptBuilder.PromptMessages messages = prompts.build(candidates, text.trim());
        return complete(List.of(client.textMessage(SYSTEM_ROLE, messages.systemMessage()),
                client.textMessage("user", messages.userMessage())), routingDecision,
                routingDecision.status() == SchemaRoutingDecision.Status.AMBIGUOUS,
                "AI response does not match the strict candidate extraction contract.");
    }

    @Override
    public ProviderExtractionResult resolveAmbiguities(String originalText, JsonNode currentSpecification,
            Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) throw new IllegalArgumentException("Ambiguity answers are required.");
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
                    Revise the specification using only the teacher answers below.
                    Preserve confirmed facts and the pinned schemaId/schemaVersion. Never invent values.
                    Keep unresolved ambiguities and add newly discovered missing required quantities from this pinned schema.

                    Original problem:
                    %s

                    Current specification:
                    %s

                    Teacher answers keyed by ambiguity code:
                    %s
                    """.formatted(originalText, objectMapper.writeValueAsString(currentSpecification),
                    objectMapper.writeValueAsString(answers));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare ambiguity resolution request.", exception);
        }
        ExtractionPromptBuilder.PromptMessages messages = prompts.build(List.of(contract), request);
        SchemaCandidate candidate = new SchemaCandidate(contract, new SchemaSelectionScore(1, 1),
                new VerificationEvidence(1, 1, 1, List.of("PINNED_SCHEMA_VERSION")), 1);
        SchemaRoutingDecision decision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "PINNED_FOR_AMBIGUITY_RESOLUTION", List.of(candidate), 1, 1);
        return complete(List.of(client.textMessage(SYSTEM_ROLE, messages.systemMessage()),
                client.textMessage("user", messages.userMessage())), decision, false,
                "AI ambiguity response does not match the pinned schema contract.");
    }

    private ProviderExtractionResult complete(List<Map<String, Object>> messages, SchemaRoutingDecision routingDecision,
            boolean forceRoutingAmbiguity, String invalidMessage) {
        List<Map<String, Object>> attemptMessages = new ArrayList<>(messages);
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            meters.summary("physlive.ai.prompt.characters").record(prompts.messageCharacterCount(attemptMessages));
            ChatCompletionClient.Completion completion = client.completeStructured(model, attemptMessages);
            try {
                JsonNode json = client.parseJson(completion.content());
                if (!json.isObject()) throw new IllegalStateException("AI response root must be a JSON object.");
                int repairedFields = StrictSpecificationValidator.removeUnknownFields(json);
                if (repairedFields > 0) {
                    meters.counter("physlive.ai.response.repaired", "reason", "unknown_fields")
                            .increment(repairedFields);
                }
                StrictSpecificationValidator.validate(json);
                SchemaCandidate candidate = StrictSpecificationValidator.validateCandidateMembership(json, routingDecision,
                        unitNormalizer);
                SchemaVersion pinned = schemaDefinitions.requireCurrentApproved(candidate.schemaId(), candidate.schemaVersion());
                SpecificationDocument parsed = objectMapper.treeToValue(json, SpecificationDocument.class);
                if (parsed == null) throw new IllegalStateException("AI response does not contain a specification.");
                return new ProviderExtractionResult(normalize(parsed, pinned, forceRoutingAmbiguity,
                        routingDecision.candidates()), null);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            } catch (Exception exception) {
                lastFailure = new IllegalStateException(exception);
            }
            if (attempt + 1 < maxAttempts) {
                try {
                    attemptMessages = new ArrayList<>(prompts.appendRetryInstruction(attemptMessages,
                            retryInstruction(lastFailure)));
                } catch (IllegalArgumentException capFailure) {
                    throw new IllegalStateException(invalidMessage + " Retry was skipped because the prompt would exceed the configured character limit.",
                            lastFailure);
                }
            }
        }
        throw new IllegalStateException(invalidMessage + " The AI failed " + maxAttempts
                + " structured-output attempt(s).", lastFailure);
    }

    private String retryInstruction(RuntimeException failure) {
        String detail = failure == null || !StringUtils.hasText(failure.getMessage())
                ? "The response did not satisfy the JSON contract."
                : failure.getMessage().replaceAll("[\\r\\n\\t]+", " ").trim();
        if (detail.length() > MAX_RETRY_ERROR_CHARACTERS) {
            detail = detail.substring(0, MAX_RETRY_ERROR_CHARACTERS);
        }
        return """
                The previous response violated the strict contract.
                Validator error: %s
                Regenerate the complete JSON object using only the original request and pinned candidate contracts.
                Each ambiguity must contain exactly: code, fieldPath, question, options.
                Check every requiredQuantities entry: provide exactly one stated quantity or one ambiguity for it. Do not ask for optionalQuantities.
                Preserve raw stated values and original units; do not add inferred values or explanatory fields.
                """.formatted(detail).trim();
    }

    private void requireCurrentCandidates(SchemaRoutingDecision routingDecision) {
        for (SchemaCandidate candidate : routingDecision.candidates()) {
            schemaDefinitions.requireCurrentApproved(candidate.schemaId(), candidate.schemaVersion());
        }
    }

    private SpecificationDocument normalize(SpecificationDocument document, SchemaVersion schema,
            boolean forceRoutingAmbiguity, List<SchemaCandidate> routedCandidates) {
        if (!schema.getVersion().equals(document.schemaVersion())) {
            throw new IllegalStateException("AI schema version does not match the pinned candidate version.");
        }
        List<PhysicalQuantity> quantities = new ArrayList<>();
        for (PhysicalQuantity quantity : document.quantities()) {
            if (!StringUtils.hasText(quantity.name()) || quantity.value() == null
                    || !StringUtils.hasText(quantity.originalUnit())) {
                throw new IllegalStateException("AI returned an incomplete physical quantity.");
            }
            UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(quantity.value(), quantity.originalUnit());
            if (!normalized.knownUnit()) throw new IllegalStateException("AI returned a unit outside the unit catalog.");
            String canonicalName = schemaDefinitions.canonicalQuantityKey(schema.getDefinition(), quantity.name());
            if (!StringUtils.hasText(canonicalName)) throw new IllegalStateException("AI quantity has no canonical schema key.");
            JsonNode quantityDefinition = findQuantityDefinition(schema.getDefinition(), canonicalName);
            if (quantityDefinition == null || !allowsUnit(quantityDefinition.path("allowedUnits"), normalized.normalizedUnit())) {
                throw new IllegalStateException("AI returned a unit outside the selected schema quantity contract.");
            }
            validatePhysicalDomain(quantityDefinition, normalized.normalizedValue(), canonicalName);
            quantities.add(new PhysicalQuantity(canonicalName, quantity.symbol(), quantity.value(), quantity.originalUnit(),
                    normalized.normalizedValue(), normalized.normalizedUnit(), clamp(quantity.confidence()), quantity.sourceText()));
        }

        JsonNode endCondition = document.endCondition();
        if (endCondition == null || endCondition.isNull()) throw new IllegalStateException("AI response is missing endCondition.");
        List<String> errors = EndConditionResolver.validateNode(endCondition,
                schema.getDefinition().path("execution").path("durationSeconds").asDouble());
        if (!errors.isEmpty()) throw new IllegalStateException("AI returned an invalid endCondition: " + String.join("; ", errors));

        List<AmbiguityItem> ambiguities = new ArrayList<>(document.ambiguities());
        if (forceRoutingAmbiguity && ambiguities.stream().noneMatch(item ->
                "schemaId".equals(item.fieldPath()) || "schema.schemaId".equals(item.fieldPath()))) {
            java.util.Set<String> usedCodes = ambiguities.stream().map(AmbiguityItem::code).collect(java.util.stream.Collectors.toSet());
            String code = "schema.selection";
            int suffix = 1;
            while (usedCodes.contains(code)) code = "schema.selection." + suffix++;
            ambiguities.add(new AmbiguityItem(code, "schemaId",
                    "Hệ thống chưa đủ bằng chứng để chắc chắn về mô hình vật lý. Vui lòng xác nhận mô hình tạm chọn.",
                    routedCandidates.stream().map(item -> item.schemaId() + "@" + item.schemaVersion()).toList()));
        }
        return new SpecificationDocument(schema.getVersion(), schema.getTopic(),
                schema.getSchemaId(), document.objects(), quantities, document.relations(), endCondition,
                clamp(document.confidence()), ambiguities, SpecificationDocument.CURRENT_SCHEMA_VERSION);
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

    private void validatePhysicalDomain(JsonNode definition, BigDecimal value, String key) {
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric) || (definition.path("positive").asBoolean(false) && numeric <= 0)
                || (definition.path("nonNegative").asBoolean(false) && numeric < 0)
                || (definition.path("integer").asBoolean(false) && value.stripTrailingZeros().scale() > 0)
                || (definition.path("min").isNumber() && numeric < definition.path("min").asDouble())
                || (definition.path("max").isNumber() && numeric > definition.path("max").asDouble())) {
            throw new IllegalStateException("AI quantity violates the physical domain for " + key + ".");
        }
    }

    private BigDecimal clamp(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
