package com.example.backend.ai.simulation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.ai.extraction.validation.ResponseSchemaValidator;
import com.example.backend.config.properties.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Converts the confirmed user description into an engine-independent simulation brief. */
@Component
public final class SimulationUnderstandingGateway {
    private static final Logger log = LoggerFactory.getLogger(SimulationUnderstandingGateway.class);
    private static final String SYSTEM_PROMPT = """
            Interpret the confirmed user description as an open-ended visual simulation brief.
            Respond in the user's language. Preserve every explicit name, quantity, count,
            relation, material, effect, interaction and requested visual idea exactly. Do not
            merge explicitly distinct participants or alter a supplied number.

            Understand meaning from context, not from a fixed object catalogue. A vague noun
            should be resolved into a natural visual form using the surrounding phenomenon. Infer
            only the setting, relationships and visual cues needed to make that phenomenon legible;
            these are contextual visual assumptions, not invented measured quantities. Disclose
            meaningful assumptions in defaults or the visual brief. Do not ask for clarification
            merely because an object shape, background, layout, camera, color, duration or
            rendering technique was not specified.

            simulationSpec.runtimeKind is only a transport/runtime marker; the server normalizes
            it to VISUAL and it must not narrow the subject, the scene, or the rendering method.
            Keep the semantic brief open: use the common fields when useful and add domain-specific
            context when the phenomenon needs it. The later generator chooses the visual
            language and may combine procedural geometry, SVG, gradients, filters, particles,
            traces, charts, labels, typography or other safe techniques. Do not choose asset
            identifiers or copy a stock composition.

            Return CLARIFY only when missing information changes the essential phenomenon or
            makes the requested behavior physically ambiguous. If the request is not a physical
            phenomenon or measured physical quantity, return UNSUPPORTED in the user's language.
            Never replace the user's phenomenon with another one.

            Preserve explicit numeric values with SI conversion where applicable. Record named
            or counted physical participants and relationships, but do not fabricate an
            exhaustive inventory for every contextual visual detail. Return valid json matching
            the response contract. The response is data only; do not generate JavaScript here.
            """;

    private final ChatCompletionClient client;
    private final AiProviderProperties properties;
    private final ObjectMapper mapper;
    private final JsonNode responseSchema;

    public SimulationUnderstandingGateway(ChatCompletionClient client, AiProviderProperties properties,
            ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
        try (var input = SimulationUnderstandingGateway.class.getResourceAsStream(
                "/prompts/simulation-understanding-response-schema.json")) {
            if (input == null) throw new IllegalStateException("Simulation understanding schema is missing");
            responseSchema = mapper.readTree(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read simulation understanding schema", failure);
        }
    }

    /**
     * Reports whether this gateway can make an LLM request. Keeping this check at
     * the flow boundary lets the API return a useful readiness error instead of
     * leaking the client's configuration exception as an opaque HTTP 500.
     */
    public boolean isAvailable() {
        return client.isAvailable() && StringUtils.hasText(properties.textModel());
    }

    public JsonNode understand(String description, JsonNode conversation) {
        var context = mapper.createObjectNode();
        context.putObject("visualBudget")
                .put("maxStateBytes", 262144)
                .put("maxDrawCommandsPerFrame", 1000)
                .put("maxPresentationSeconds", 40);
        context.set("responseContract", responseSchema);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(client.textMessage("system", SYSTEM_PROMPT));
        messages.add(client.textMessage("user", "Visual budget and output contract:\n" + context));
        messages.add(client.textMessage("user", "confirmedDescription — source text:\n" + description));
        if (conversation != null) {
            for (JsonNode revision : conversation) {
                messages.add(client.textMessage("user", "User clarification or correction:\n" + revision.asText()));
            }
        }
        JsonNode result = client.parseJson(client.completeJsonReasoned(properties.textModel(), messages).content());
        if (Boolean.getBoolean("physlive.simulation-live-e2e")) {
            log.debug("SIMULATION_LIVE_UNDERSTANDING={}", result);
        }
        ResponseSchemaValidator.validate(result, responseSchema);
        separateQualitativeValues(result.path("simulationSpec"));
        return result;
    }

    static void separateQualitativeValues(JsonNode specification) {
        if (!(specification instanceof ObjectNode mutable)
                || !specification.path("fixedQuantities").isArray()) return;
        ArrayNode explicit = mutable.arrayNode();
        ArrayNode qualitative = mutable.arrayNode();
        for (JsonNode quantity : specification.path("fixedQuantities")) {
            if ("ASSUMPTION".equals(quantity.path("provenance").asText())) qualitative.add(quantity);
            else explicit.add(quantity);
        }
        mutable.set("fixedQuantities", explicit);
        mutable.set("qualitativeValues", qualitative);
    }
}
