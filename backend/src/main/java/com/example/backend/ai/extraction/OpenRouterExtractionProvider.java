package com.example.backend.ai.extraction;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.backend.config.properties.OpenRouterProperties;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.ai.client.OpenRouterClient;
import com.example.backend.ai.extraction.model.PhysicalQuantity;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.normalization.UnitNormalizer;

@Component
public class OpenRouterExtractionProvider implements ExtractionProvider {

    private static final String SYSTEM_ROLE = "system";

    private final OpenRouterClient client;
    private final ObjectMapper objectMapper;
    private final UnitNormalizer unitNormalizer;
    private final SchemaDefinitionService schemaDefinitions;
    private final String model;
    private final String baseSystemPrompt;

    public OpenRouterExtractionProvider(OpenRouterClient client, ObjectMapper objectMapper,
            UnitNormalizer unitNormalizer, SchemaDefinitionService schemaDefinitions, OpenRouterProperties properties,
            ResourceLoader resourceLoader) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.unitNormalizer = unitNormalizer;
        this.schemaDefinitions = schemaDefinitions;
        this.model = properties.model();
        try (var input = resourceLoader.getResource(properties.systemPromptResource()).getInputStream()) {
            this.baseSystemPrompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the configured AI system prompt", exception);
        }
    }

    @Override
    public String providerName() {
        return "openrouter";
    }

    @Override
    public String modelVersion() {
        return model;
    }

    @Override
    public ExtractionPath path() {
        return ExtractionPath.OPENROUTER;
    }

    @Override
    public boolean isAvailable() {
        return client.isAvailable() && StringUtils.hasText(model);
    }

    @Override
    public ProviderExtractionResult extract(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Problem text must not be blank.");
        }
        return complete(List.of(
                client.textMessage(SYSTEM_ROLE, systemPrompt()),
                client.textMessage("user", text.trim())),
                "OpenRouter response does not match Specification v1.");
    }

    @Override
    public ProviderExtractionResult resolveAmbiguities(String originalText, JsonNode currentSpecification,
            Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) throw new IllegalArgumentException("Ambiguity answers are required.");
        String request;
        try {
            request = """
                    Revise the complete physics specification using the teacher answers below.
                    Preserve confirmed facts. Never invent values. Keep any ambiguity that an answer does not fully resolve,
                    preserve unrelated ambiguities, and add newly discovered missing required fields from the supplied schema.

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
        return complete(List.of(client.textMessage(SYSTEM_ROLE, systemPrompt()), client.textMessage("user", request)),
                "OpenRouter ambiguity response does not match Specification v1.");
    }

    private ProviderExtractionResult complete(List<Map<String, Object>> messages, String invalidMessage) {
        List<Map<String, Object>> attemptMessages = new ArrayList<>(messages);
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            OpenRouterClient.Completion completion = client.complete(model, attemptMessages);
            try {
                JsonNode json = client.parseJson(completion.content());
                if (!json.isObject()) throw new IllegalStateException("AI response root must be a JSON object.");
                SpecificationDocument parsed = objectMapper.treeToValue(json, SpecificationDocument.class);
                if (parsed == null || !StringUtils.hasText(parsed.schemaId())) {
                    throw new IllegalStateException("AI response does not contain a specification.");
                }
                return new ProviderExtractionResult(normalize(parsed), completion.rawResponse());
            } catch (RuntimeException exception) {
                lastFailure = exception;
            } catch (Exception exception) {
                lastFailure = new IllegalStateException(exception);
            }
            attemptMessages.add(client.textMessage("assistant", completion.content()));
            attemptMessages.add(client.textMessage("user", """
                    The previous response was invalid or incomplete. Regenerate the entire specification as one
                    complete JSON object. Do not continue the truncated response. Keep ambiguity codes unique and
                    use quantities.<canonical schema key> for every missing required quantity fieldPath.
                    """));
        }
        throw new IllegalStateException(invalidMessage + " The AI failed two structured-output attempts.", lastFailure);
    }

    private SpecificationDocument normalize(SpecificationDocument document) {
        String schemaId = document.schemaId();
        SchemaVersion schema = schemaDefinitions.requireApproved(schemaId);
        String topic = schema.getTopic();

        List<PhysicalQuantity> quantities = new ArrayList<>();
        for (PhysicalQuantity quantity : document.quantities()) {
            if (!StringUtils.hasText(quantity.name()) || quantity.value() == null
                    || !StringUtils.hasText(quantity.originalUnit())) {
                throw new IllegalStateException("OpenRouter returned an incomplete physical quantity.");
            }
            UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(
                    quantity.value(), quantity.originalUnit());
            if (!normalized.knownUnit()) {
                throw new IllegalStateException("OpenRouter returned an unsupported unit: " + quantity.originalUnit());
            }
            String canonicalName = schemaDefinitions.canonicalQuantityKey(schema.getDefinition(), quantity.name());
            if (!StringUtils.hasText(canonicalName)) {
                throw new IllegalStateException("OpenRouter returned a quantity without a canonical schema key.");
            }
            quantities.add(new PhysicalQuantity(
                    canonicalName, quantity.symbol(), quantity.value(), quantity.originalUnit(),
                    normalized.normalizedValue(), normalized.normalizedUnit(), clamp(quantity.confidence()),
                    quantity.sourceText()));
        }

        if (document.endCondition() == null || document.endCondition().isNull()) {
            throw new IllegalStateException("OpenRouter response is missing the required endCondition.");
        }
        JsonNode endCondition = document.endCondition();
        List<String> endConditionErrors = EndConditionResolver.validateNode(endCondition,
                schema.getDefinition().path("execution").path("durationSeconds").asDouble());
        if (!endConditionErrors.isEmpty()) {
            throw new IllegalStateException("OpenRouter returned an invalid endCondition: "
                    + String.join("; ", endConditionErrors));
        }

        return new SpecificationDocument(
                SpecificationDocument.CURRENT_SCHEMA_VERSION, topic, schemaId, document.objects(), quantities,
                document.relations(), endCondition, clamp(document.confidence()), document.ambiguities());
    }

    private String systemPrompt() {
        try {
            List<Map<String, Object>> catalog = schemaDefinitions.approvedSchemas().stream()
                    .map(schema -> Map.<String, Object>of(
                            "schemaId", schema.getSchemaId(), "topic", schema.getTopic(),
                            "definition", schema.getDefinition()))
                    .toList();
            return baseSystemPrompt + "\n\nApproved schema catalog:\n" + objectMapper.writeValueAsString(catalog);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare approved schema catalog for AI extraction.", exception);
        }
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
