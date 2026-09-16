package com.example.backend.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.backend.service.SchemaDefinitionService;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.physics.EndConditionResolver;

@Component
public class OpenRouterExtractionProvider implements ExtractionProvider {

    private static final String DEFAULT_MODEL = "openrouter/free";
    private static final String SYSTEM_ROLE = "system";
    private static final String SYSTEM_PROMPT = """
            You are the PhysLive Problem Understanding Engine.
            Read Vietnamese or English physics problems by meaning, not by keyword matching.
            Return only valid JSON with this exact contract:
            {"schemaVersion":"1.0","topic":"KINEMATICS|DYNAMICS|CIRCUITS|null",
            "schemaId":"one approved schemaId from the supplied catalog",
            "objects":[{"id":"string","label":"string","type":"string"}],
            "quantities":[{"name":"string","symbol":"string|null","value":0,"originalUnit":"string",
            "normalizedValue":0,"normalizedUnit":"string","confidence":0.0,"sourceText":"string"}],
            "relations":[{"type":"string","subject":"string","object":"string|null","value":"number|string|boolean|null","unit":"string|null","sourceText":"string"}],
            "endCondition":{"type":"time_limit", "duration":10},
            "confidence":0.0,
            "ambiguities":[{"code":"stable.machine.code","fieldPath":"concrete.path","question":"one concise Vietnamese teacher-facing question","options":["explicit answer with unit"]}]}.
            Select schemaId from the supplied approved schema catalog. Use canonical quantity keys and execution relation types from that schema.
            Choose exactly one declarative endCondition from time_limit, threshold, event, cycle_count or manual.
            threshold requires quantity/operator/value; event requires event.type (contact or collision) and
            event.entities; cycle_count requires quantity/count; manual may have maxTime.
            Map explicit context such as a fixed observation duration, a target position, contact/collision,
            or a number of cycles to that generic type. Never invent a physics-specific type and never compute
            the resolved end time; Java resolves it from solver output. Dynamic conditions must include maxTime
            when the problem gives a safety horizon; otherwise the engine applies its bounded safety horizon.
            Detect missing required parameters, underspecified initial conditions, reference frames and implicit assumptions.
            For a missing required quantity, fieldPath must be exactly quantities.<canonical schema key>.
            Every ambiguity code must be unique in the response. Return at most one ambiguity for each unresolved fieldPath.
            If a quantity value is missing, do not include that quantity in quantities. Never emit 0 or any other placeholder.
            Never invent a missing value and never silently assume it. Put every uncertainty in ambiguities.
            Use SI values in normalizedValue and normalizedUnit. Return JSON only.
            """;

    private final OpenRouterClient client;
    private final ObjectMapper objectMapper;
    private final UnitNormalizer unitNormalizer;
    private final SchemaDefinitionService schemaDefinitions;
    private final String model;

    public OpenRouterExtractionProvider(OpenRouterClient client, ObjectMapper objectMapper,
            UnitNormalizer unitNormalizer, SchemaDefinitionService schemaDefinitions, Environment environment) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.unitNormalizer = unitNormalizer;
        this.schemaDefinitions = schemaDefinitions;
        this.model = environment.getProperty("OPENROUTER_MODEL", DEFAULT_MODEL).trim();
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

    public ProviderExtractionResult resolveAmbiguity(String originalText, JsonNode currentSpecification,
            String ambiguityCode, String fieldPath, String question, String answer) {
        if (!StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("Ambiguity answer must not be blank.");
        }
        String request;
        try {
            request = """
                    Revise the complete physics specification using the teacher's answer.
                    Preserve all confirmed facts. Do not infer by keywords and do not invent values.
                    Remove the answered ambiguity. Keep unrelated unresolved ambiguities.

                    Original problem:
                    %s

                    Current specification:
                    %s

                    Ambiguity code: %s
                    Field path: %s
                    Question: %s
                    Teacher answer: %s
                    """.formatted(originalText, objectMapper.writeValueAsString(currentSpecification),
                    ambiguityCode, fieldPath, question, answer.trim());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare ambiguity resolution request.", exception);
        }
        return complete(List.of(
                client.textMessage(SYSTEM_ROLE, systemPrompt()),
                client.textMessage("user", request)),
                "OpenRouter ambiguity response does not match Specification v1.");
    }

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
            quantities.add(new PhysicalQuantity(
                    quantity.name(), quantity.symbol(), quantity.value(), quantity.originalUnit(),
                    normalized.normalizedValue(), normalized.normalizedUnit(), clamp(quantity.confidence()),
                    quantity.sourceText()));
        }

        JsonNode endCondition = document.endCondition() == null
                ? EndConditionResolver.normalize(null, schema.getDefinition().path("execution").path("durationSeconds").asDouble(10))
                : document.endCondition();
        List<String> endConditionErrors = EndConditionResolver.validateNode(endCondition,
                schema.getDefinition().path("execution").path("durationSeconds").asDouble(10));
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
            return SYSTEM_PROMPT + "\n\nApproved schema catalog:\n" + objectMapper.writeValueAsString(catalog);
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
