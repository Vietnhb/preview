package com.example.backend.matter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.ai.extraction.validation.ResponseSchemaValidator;
import com.example.backend.config.properties.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Provider calls for understanding and a data-only scene plan. No executable AI output. */
@Component
public class MatterAiGateway {
    private static final Logger log = LoggerFactory.getLogger(MatterAiGateway.class);
    private static final String UNDERSTAND_PROMPT = """
            Interpret the confirmed description within Physics grades 10, 11 and 12,
            from the user's description and generally accepted high-school physics. Respond in the
            user's language with one JSON object following the response contract. All
            explanation, question, defaults and participant labels must use the language
            of confirmedDescription, regardless of the language of these instructions
            Copy named participants from the source.
            Carefully identify and distinguish every individual physical object, participant,
            surface, and medium mentioned in confirmedDescription (e.g. specific vehicles,
            projectiles, pendulums, blocks, pulleys, fluids, celestial bodies). Preserve the
            exact user-given terminology and character for each participant. Do not merge
            or generalize distinct objects into generic placeholders.
            Later user additions or corrections override earlier gaps or alternatives;
            do not ask again for an interaction the user has already specified.
            Select simulationSpec.runtimeKind directly from the phenomenon: MATTER
            for a rigid-body scene fully represented by its declared Matter.js primitives;
            VISUAL for an AI-authored numerical/visual model when other governing behavior
            or a recognizable depiction of named real-world participants is required.
            Use MATTER only when geometric rigid bodies themselves honestly represent
            every participant. Topic identity does not force a runtime. Preserve the requested
            phenomenon rather than simplifying it to fit a rigid-body engine.
            Inspect runtimeLimits before choosing: use a declared VISUAL capability
            when a Matter representation would exceed its body, link, numeric or time
            budget. Preserve every requested object and value rather than truncating.
            Matter scenes run at physical elapsed time for at most 40 seconds. Choose
            VISUAL when an explicit physical duration needs a different presentation
            clock, and disclose that time mapping while preserving the physical duration.
            Ask exactly one question only when the missing interaction identity or
            participating objects would change the essential phenomenon. Missing
            numerical parameters, scale, initial coordinates, separation, size,
            duration, or display geometry are never by themselves reasons to clarify.
            Choose reasonable values for those gaps, disclose every default clearly,
            and explain the resulting behavior. Never change a number the user supplied.
            When ready, briefly explain the governing physics, the requested objects,
            initial state, given values, assumptions, and what will be drawn. Distinguish
            centre coordinates from edge or surface distances when it matters. Do not
            predict unverified numerical outcomes. Set externalForces, friction, and
            conservativeInteractions from the modeled system for later validation.
            If the essential phenomenon cannot be modeled honestly with either runtime,
            explain the limitation instead of substituting a different phenomenon.
            A generated visual model may represent non-rigid
            physics when its governing law and assumptions are grounded in the confirmed
            description and high-school physics. Do not generate code at this stage.
            The request must concern a physical phenomenon or measured physical
            quantities within high-school physics. If it does not, return UNSUPPORTED;
            data or chart language alone does not make a nonphysical request in scope.
            Use null or [] for nonapplicable schema fields and keep all required fields.
            Never return only the first or most important participant. simulationSpec is
            a complete inventory contract: every distinct participant and every member
            of a stated count must be representable by the generated program.
            """;

    private static final String INVENTORY_PROMPT = """
            For EXPLAIN, include the source inventory in simulationSpec. requiredObjects
            includes every requested physical participant, preserving names and counts;
            separate entries with different properties or roles, group only identical
            participants. Each label must identify the source participant, retaining
            its given name; do not replace names with generic category labels.
            shape is circle, rectangle or unspecified, without forcing a
            non-rigid participant into a mechanical shape. requiredConstraints contains
            physical connections, not contact events. fixedQuantities preserves every
            user-stated physical value, converted to appropriate SI units with affected
            object/link indexes; use empty indexes for global values. EXPLICIT means
            source-stated; any inferred value is ASSUMPTION and also belongs in defaults.
            Do not treat an identifying numeral as a quantity. Use a direct sceneField
            when applicable, DERIVED when no direct field exists. Keep spatial relations
            and reference objects explicit; canvas x is right and y is down. Give short
            source quotes for review. Never invent participants or source-given values.
            """;

    private static final String SCENE_PROMPT = """
            Produce a numeric SI-unit scene matching the confirmed description and JSON
            schema. Do not write code or asset IDs. Preserve each user-stated object,
            physical link, quantity, and initial relationship; use only disclosed defaults
            where details are missing. Do not merge distinct requested objects.
            Each body must include sourceObjectIndex, the zero-based index of its matching
            entry in simulationSpec.requiredObjects. Each physical link must include
            sourceConstraintIndex from simulationSpec.requiredConstraints. Repeat an index
            for each member of a requested group so its count is preserved. Do not invent
            physical scene bodies or links unrelated to the confirmed inventory.
            Scene coordinates have x right and y down. A circle has positive radius
            with width and height 0; a rectangle has positive width and height with
            radius 0. A static body's mass is a positive placeholder even though the
            engine treats it as immovable. Choose any missing geometry from the defaults
            disclosed in the confirmed explanation.
            Essential contacts and collisions belong in expectedContacts, never constraints.
            Every constraint needs at least one body endpoint.
            Keep stated relative positions
            physically consistent with body extents and interpret distances from centres
            or surfaces according to the user's wording. Use positive body dimensions and
            mass; fixed bodies have no initial velocity. Use valid body IDs for link and
            expected-contact endpoints. The backend derives local sliders and draws the
            scene; numerical resolution concerns are checked after display.
            If the essential phenomenon cannot be represented by the available runtime,
            return UNSUPPORTED with a reason in the user's language. Never substitute a
            different phenomenon for the one requested.
            """;

    private final ChatCompletionClient client;
    private final AiProviderProperties properties;
    private final ObjectMapper mapper;
    private final JsonNode understandingSchema;
    private final JsonNode sceneSchema;

    public MatterAiGateway(ChatCompletionClient client, AiProviderProperties properties, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
        try (var understanding = MatterAiGateway.class.getResourceAsStream(
                "/prompts/matter-understanding-response-schema.json");
             var scene = MatterAiGateway.class.getResourceAsStream(
                "/prompts/matter-scene-response-schema.json")) {
            if (understanding == null || scene == null) {
                throw new IllegalStateException("Matter structured-output schemas are missing");
            }
            this.understandingSchema = mapper.readTree(understanding);
            this.sceneSchema = mapper.readTree(scene);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read Matter structured-output schemas", failure);
        }
    }

    public JsonNode understand(String description, JsonNode conversation) {
        var inputNode = mapper.createObjectNode();
        var budgets = inputNode.putObject("runtimeLimits");
        budgets.putObject("MATTER")
                .put("maxBodies", MatterSceneCompiler.MAX_BODIES)
                .put("maxConstraints", MatterSceneCompiler.MAX_CONSTRAINTS)
                .put("maxAbsoluteSceneValueSI", MatterSceneCompiler.MAX_ABSOLUTE)
                .put("minPhysicalDurationSeconds", 0.1)
                .put("maxPhysicalDurationSeconds", 40);
        inputNode.set("responseContract", understandingSchema);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(client.textMessage("system", UNDERSTAND_PROMPT + INVENTORY_PROMPT));
        messages.add(client.textMessage("user", "Runtime limits and output contract:\n" + inputNode));
        messages.add(client.textMessage("user", "confirmedDescription — source text:\n" + description));
        for (JsonNode revision : conversation) {
            messages.add(client.textMessage("user", "User clarification or correction:\n" + revision.asText()));
        }
        JsonNode result = client.parseJson(client.completeJsonReasoned(properties.textModel(), messages).content());
        if (Boolean.getBoolean("physlive.matter-live-e2e")) {
            log.debug("MATTER_LIVE_UNDERSTANDING={}", result);
        }
        ResponseSchemaValidator.validate(result, understandingSchema);
        separateQualitativeValues(result.path("simulationSpec"));
        return result;
    }

    public JsonNode plan(String description, String explanation, JsonNode specification) {
        var context = mapper.createObjectNode();
        context.put("description", description);
        context.put("confirmedExplanation", explanation);
        context.set("simulationSpec", specification);
        JsonNode result = client.parseJson(client.completeWithSchemaReasoned(properties.textModel(), List.of(
                client.textMessage("system", SCENE_PROMPT + capacityInstruction()),
                client.textMessage("user", context.toString())),
                "matter_scene", sceneSchema).content());
        if (Boolean.getBoolean("physlive.matter-live-e2e")) {
            log.debug("MATTER_LIVE_PLAN={}", result);
        }
        return result;
    }

    static void separateQualitativeValues(JsonNode inventory) {
        if (!(inventory instanceof ObjectNode mutable)
                || !inventory.path("fixedQuantities").isArray()) return;
        ArrayNode explicit = mutable.arrayNode();
        ArrayNode qualitative = mutable.arrayNode();
        for (JsonNode quantity : inventory.path("fixedQuantities")) {
            if ("ASSUMPTION".equals(quantity.path("provenance").asText())) {
                qualitative.add(quantity);
            } else {
                explicit.add(quantity);
            }
        }
        mutable.set("fixedQuantities", explicit);
        mutable.set("qualitativeValues", qualitative);
    }

    private String capacityInstruction() {
        return "\nThe runtime limit is " + MatterSceneCompiler.MAX_BODIES + " bodies and "
                + MatterSceneCompiler.MAX_CONSTRAINTS + " constraints. Never truncate or merge a request.\n";
    }
}
