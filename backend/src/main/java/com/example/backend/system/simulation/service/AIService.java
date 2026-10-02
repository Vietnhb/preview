package com.example.backend.system.simulation.service;

import com.example.backend.config.AIProperties;
import com.example.backend.config.JevProperties;
import com.example.backend.config.UploadProperties;
import com.example.backend.exception.ApiException;
import com.example.backend.integration.ai.AIClient;
import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.physics.service.SchemaDefinitionService;
import com.example.backend.system.physics.service.SchemaEquationRuntime;
import com.example.backend.system.simulation.dto.SimulationRequests;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class AIService {
    private static final String NO_MATCH = "NO_MATCH";
    private final SchemaDefinitionService schemas;
    private final SchemaEquationRuntime equations;
    private final GeneratedSimulationStorage storage;
    private static final int ROUTING_SUMMARY_MAX = 1400;
    private final JevProperties jev;
    private final ObjectMapper json;
    private final AIClient client;
    private final AIProperties ai;
    private final UploadProperties upload;
    private final byte[] signingKey;
    private final int maxProgramCharacters;

    public AIService(SchemaDefinitionService schemas, SchemaEquationRuntime equations,
            GeneratedSimulationStorage storage, JevProperties jev, ObjectMapper json,
            AIProperties ai, AIClient client, UploadProperties upload, @Value("${jwt.secret}") String signingKey,
            @Value("${physlive.simulation.runtime.max-program-part-characters}") int maxProgramCharacters) {
        this.schemas = schemas;
        this.equations = equations;
        this.storage = storage;
        this.jev = jev;
        this.json = json;
        this.ai = ai;
        this.upload = upload;
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        this.maxProgramCharacters = maxProgramCharacters;
        this.client = client;
    }

    public JsonNode understandText(String description, String sessionId, String recognizedText, String correctedText) {
        if (sessionId != null && !sessionId.isBlank()) {
            String confirmedText = correctedText == null || correctedText.isBlank() ? recognizedText : correctedText;
            if (confirmedText == null || confirmedText.isBlank()) {
                throw ApiException.badRequest("Confirmed OCR text is required");
            }
            return understand(confirmedText, sessionId);
        }
        if (description == null || description.isBlank()) {
            throw ApiException.badRequest("Simulation description is required");
        }
        return understand(description, UUID.randomUUID().toString());
    }

    public ObjectNode generate(JsonNode request) {
        requireSignedPlan(request);
        SchemaVersion schema = selectedSchema(request);
        JsonNode brief = request.path("simulationSpec");
        ObjectNode computed = equations.compute(schema.getDefinition(), brief, json.createObjectNode());
        ObjectNode input = json.createObjectNode();
        input.put("description", request.path("description").asText());
        // The planner's plain-language explanation carries the teaching intent (what to
        // notice).
        if (request.path("explanation").isTextual())
            input.put("planExplanation", request.path("explanation").asText());
        input.set("confirmedBrief", visualBrief(brief));
        JsonNode diagnostics = request.path("renderDiagnostics");
        if (!diagnostics.isMissingNode()) {
            if (!diagnostics.path("code").isTextual() || !diagnostics.path("message").isTextual()
                    || diagnostics.path("code").asText().length() > 3 * maxProgramCharacters
                    || diagnostics.path("message").asText().length() > 4000)
                throw ApiException.badRequest("Invalid rendering diagnostics");
            input.set("renderDiagnostics", diagnostics);
        }
        ObjectNode fieldMeta = solverFieldMeta(schema.getDefinition(), brief);
        input.set("solverFields", visualFields(fieldMeta, computed.path("solverTimeline").path("frames")));
        JsonNode renderingContract = jsonResource("prompts/simulation-response-schema.json");
        ObjectNode visual = (ObjectNode) client.visual(input,
                resource("prompts/simulation-visual-system.txt"),
                renderingContract);
        JsonNode program = visual.path("visualProgram");
        java.util.List<String> unbound = unboundParticipants(program, brief);
        if (!unbound.isEmpty()) {
            // Validation step 1 (generic, data-bound): every participant the solver
            // computes is shown through a kit binding (body or instrument) so the renderer
            // can cross-check the stage against the verified solver timeline. Give the
            // director one chance to fix it. Whether the program really uses the kit is
            // checked by the renderer at run time, not by matching the code text.
            ObjectNode retryDiagnostics = input.putObject("renderDiagnostics");
            retryDiagnostics.put("code", truncate(program.toString(), 3 * maxProgramCharacters));
            retryDiagnostics.put("message", "Not bound to solver data: participant(s) " + String.join(", ", unbound)
                    + " have no body in scene.bodies, no instrument in scene.instruments and are never bound in code,"
                    + " so the learner cannot see their computed behaviour. Keep your design, but give every participant"
                    + " a kit binding (moving body or instrument) and keep your own drawings static.");
            JsonNode retried = client.visual(input,
                    resource("prompts/simulation-visual-system.txt"),
                    renderingContract);
            if (retried.isObject() && unboundParticipants(retried.path("visualProgram"), brief).size() < unbound.size()) {
                visual = (ObjectNode) retried;
                program = visual.path("visualProgram");
            }
        }
        String code = program.path("code").asText("").trim();
        JsonNode scene = program.path("scene");
        boolean hasScene = scene.isObject() && (scene.path("bodies").size() > 0
                || !scene.path("environment").asText("").isBlank());
        if (!program.isObject() || (!hasScene && code.isEmpty()))
            throw ApiException.upstream("Generated visual has neither SVG scene artwork nor PixiJS code");
        if (code.length() > maxProgramCharacters || (hasScene && scene.toString().length() > 2L * maxProgramCharacters))
            throw ApiException.upstream("Generated visual exceeds the code/artwork budget");
        if (!code.isEmpty() && (!code.startsWith("async function") || !code.contains("update")))
            throw ApiException.upstream("Generated PixiJS program must be an async function(PIXI, app, api) returning {update}");
        ObjectNode spec = (ObjectNode) brief.deepCopy();
        spec.remove("scene");
        spec.set("visualProgram", program);
        spec.set("solverTimeline", computed.path("solverTimeline"));
        spec.set("solverFieldMeta", fieldMeta);
        if (program.path("design").isObject())
            spec.set("visualDesign", program.path("design"));
        spec.put("runtimeKind", "SVG_PIXI");
        ObjectNode response = json.createObjectNode();
        response.put("sessionId", request.path("sessionId").asText(UUID.randomUUID().toString()));
        response.put("stage", "SIMULATION");
        response.set("code", program.path("code"));
        response.put("schemaId", schema.getSchemaId());
        response.put("schemaVersion", schema.getVersion());
        response.put("description", request.path("description").asText());
        response.set("planSignature", request.path("planSignature"));
        response.set("parameters", spec.path("parameters"));
        response.set("simulationSpec", spec);
        ObjectNode validation = (ObjectNode) computed.path("validation");
        validation.put("topicVersion", schema.getVersion());
        response.set("validation", validation);
        return response;
    }

    public ObjectNode compute(JsonNode request) {
        requireSignedPlan(request);
        SchemaVersion schema = selectedSchema(request);
        ObjectNode result = equations.compute(schema.getDefinition(), request.path("simulationSpec"),
                request.path("parameters"));
        ((ObjectNode) result.path("validation")).put("topicVersion", schema.getVersion());
        return result;
    }

    public LibraryItemResponse save(SimulationRequests.Save request) {
        requireSignedPlan(request.simulation());
        SchemaVersion schema = selectedSchema(request.simulation());
        ObjectNode computed = equations.compute(schema.getDefinition(), request.simulation().path("simulationSpec"),
                request.parameters());
        ((ObjectNode) computed.path("validation")).put("topicVersion", schema.getVersion());
        return storage.save(request, schema, computed);
    }

    public ObjectNode openSaved(UUID id) {
        return storage.open(id);
    }

    public void updateSavedVisual(UUID id, ObjectNode generated) {
        requireSignedPlan(generated);
        storage.updateVisual(id, generated);
    }

    private SchemaVersion selectedSchema(JsonNode request) {
        return schemas.requireCurrentApproved(request.path("schemaId").asText(),
                request.path("schemaVersion").asText());
    }

    private String signPlan(JsonNode request) {
        ObjectNode contract = json.createObjectNode();
        JsonNode spec = request.path("simulationSpec");
        for (String field : java.util.List.of("durationSeconds", "durationParameter", "parameters", "physicsModels",
                "physicsCoverage"))
            contract.set(field, spec.path(field));
        String payload = "physlive-simulation-plan-v1\n" + request.path("schemaId").asText() + "\n"
                + request.path("schemaVersion").asText() + "\n" + request.path("description").asText() + "\n"
                + schemas.compiledChecksum(contract);
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign simulation plan", ex);
        }
    }

    private void requireSignedPlan(JsonNode request) {
        String actual = request.path("planSignature").asText();
        if (!java.security.MessageDigest.isEqual(signPlan(request).getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)))
            throw ApiException.conflict("The simulation plan changed; submit the revised description for understanding first");
    }

    public ObjectNode understandImage(byte[] imageBytes, String mediaType, String text) {
        if (imageBytes == null || imageBytes.length == 0 || imageBytes.length > upload.maxImageBytes()) {
            throw ApiException.badRequest("Image is empty or exceeds the configured size limit");
        }
        if (mediaType == null || !upload.allowedImageTypes().contains(mediaType.toLowerCase(java.util.Locale.ROOT))) {
            throw ApiException.unsupportedMedia("Image type is not allowed");
        }
        if (text != null && text.length() > jev.maximumQueryCharacters()) {
            throw ApiException.badRequest("Image context text is too long");
        }
        JsonNode transcription = client.transcribe(imageBytes, mediaType, text, () -> {
            try {
                return new DefaultResourceLoader().getResource(ai.provider().ocrPromptResource())
                        .getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw ApiException.upstream("Vision provider returned an invalid transcription response");
            }
        });
        String recognized = transcription.path("text").asText("").trim();
        ObjectNode result = json.createObjectNode();
        result.put("sessionId", UUID.randomUUID().toString());
        result.put("stage", recognized.isEmpty() ? "RECOGNITION_FAILED" : "RECOGNITION");
        result.put("recognizedText", recognized);
        result.put("displayText", recognized);
        result.put("sourceMode", "IMAGE");
        result.putNull("confidence");
        if (recognized.isEmpty())
            result.put("message", "Không nhận diện được nội dung rõ ràng từ ảnh.");
        return result;
    }

    private JsonNode understand(String description, String sessionId) {
        if (description.length() > jev.maximumQueryCharacters()) {
            throw ApiException.badRequest("Simulation description exceeds the configured limit");
        }
        var approved = schemas.approvedSchemas();
        if (approved.isEmpty()) {
            throw ApiException.unprocessable("No approved topic schema is available");
        }
        Map<String, SchemaVersion> byId = new LinkedHashMap<>();
        ObjectNode criteria = json.createObjectNode();
        // Reserve room for the JSON envelope/instructions; never spend more than
        // ROUTING_SUMMARY_MAX characters per topic even when the limit would allow it.
        int schemaTextBudget = Math.min(ROUTING_SUMMARY_MAX, Math.max(0,
                (jev.maximumPromptCharacters() - description.length() - 2048) / (approved.size() + 1) - 64));
        if (schemaTextBudget == 0) {
            throw ApiException.unprocessable("Simulation description leaves no room for approved schema routing context");
        }
        approved.forEach(schema -> {
            byId.put(schema.getSchemaId(), schema);
            criteria.put(schema.getSchemaId(), schemaDescription(schema, schemaTextBudget));
        });
        criteria.put(NO_MATCH, "The description has no suitable physical topic among the available schemas.");

        ObjectNode question = json.createObjectNode();
        question.put("type", "choice");
        question.put("instructions",
                "Choose the one approved physics topic schema that best covers the physical laws and relationships described. Choose NO_MATCH when none is suitable.");
        question.set("criteria", criteria);
        ObjectNode questions = json.createObjectNode();
        questions.set("topic_schema", question);
        JsonNode answer = client.route(description, questions).path("topic_schema");
        String selectedId = answer.path("choice").asText("");
        double confidence = answer.path("confidence").asDouble(0);
        double margin = margin(answer.path("probabilities"), selectedId);
        SchemaVersion selected = byId.get(selectedId);
        // Topic schemas follow the curriculum strands and deliberately share
        // capabilities
        // (e.g. free fall is taught in both kinematics and dynamics), so a narrow
        // margin between
        // two real topics is not ambiguity. Only a narrow margin against NO_MATCH is.
        boolean ambiguousWithNoMatch = margin < jev.minimumMargin()
                && NO_MATCH.equals(runnerUp(answer.path("probabilities"), selectedId));
        if (selectedId.isBlank() || NO_MATCH.equals(selectedId) || selected == null
                || confidence < jev.minimumConfidence() || ambiguousWithNoMatch) {
            ObjectNode clarify = json.createObjectNode();
            clarify.put("sessionId", sessionId);
            clarify.put("description", description);
            clarify.put("stage", "CLARIFY");
            clarify.put("question",
                    "Mô tả chưa xác định rõ chủ đề vật lý hiện có. Bạn có thể bổ sung hiện tượng hoặc quy luật đang muốn mô phỏng không?");
            clarify.put("schemaConfidence", confidence);
            return clarify;
        }

        JsonNode result = askLlm(description, selected);
        ObjectNode response = result.isObject() ? (ObjectNode) result.deepCopy() : json.createObjectNode();
        if (!response.hasNonNull("stage") && response.hasNonNull("status")) {
            response.set("stage", response.get("status").deepCopy());
        }
        response.put("sessionId", sessionId);
        response.put("schemaId", selected.getSchemaId());
        response.put("topic", selected.getTopic());
        response.put("schemaVersion", selected.getVersion());
        response.put("description", description);
        JsonNode spec = response.path("simulationSpec");
        if (spec.isObject()) {
            ((ObjectNode) spec).put("schemaId", selected.getSchemaId());
            ((ObjectNode) spec).put("topic", selected.getTopic());
            ((ObjectNode) spec).put("topicVersion", selected.getVersion());
            if (response.path("stage").asText().equals("EXPLAIN")) {
                reconcileParticipantCount(description, selected, response);
                ObjectNode preview = previewWithRepair(description, selected, response);
                response.set("validation", preview.path("validation"));
                response.put("planSignature", signPlan(response));
            }
            var formulas = response.putArray("formulas");
            for (JsonNode model : response.path("simulationSpec").path("physicsModels")) {
                for (JsonNode capability : selected.getDefinition().path("capabilities")) {
                    if (model.path("capabilityId").asText().equals(capability.path("capabilityId").asText())) {
                        ObjectNode formula = formulas.addObject();
                        formula.put("modelId", model.path("id").asText());
                        formula.put("label", model.path("label").asText(model.path("id").asText()));
                        formula.put("capabilityId", capability.path("capabilityId").asText());
                        formula.set("canonical", capability.path("equationSet").path("canonical"));
                        formula.set("derived", capability.path("equationSet").path("derived"));
                        formula.set("assumptions", capability.path("assumptions"));
                        formula.set("bindings", formulaBindings(selected.getDefinition(), capability, model,
                                response.path("simulationSpec")));
                    }
                }
            }

        }
        return response;
    }

    /**
     * Compute the preview; when the model's plan is internally inconsistent, first
     * apply mechanical normalisations, then give the model one chance to correct
     * the plan using the solver's own error message.
     */
    private ObjectNode previewWithRepair(String description, SchemaVersion selected, ObjectNode response) {
        normalizePlan((ObjectNode) response.path("simulationSpec"));
        try {
            return equations.compute(selected.getDefinition(), response.path("simulationSpec"),
                    json.createObjectNode());
        } catch (ApiException first) {
            ObjectNode feedback = json.createObjectNode();
            feedback.put("error", String.valueOf(first.getMessage()));
            feedback.set("previousPlan", response.path("simulationSpec"));
            JsonNode retry = askLlm(description, selected, feedback);
            JsonNode spec = retry.path("simulationSpec");
            if (!spec.isObject() || !"EXPLAIN".equals(retry.path("status").asText(retry.path("stage").asText())))
                throw planFailure(first);
            ObjectNode fixed = (ObjectNode) spec.deepCopy();
            fixed.put("schemaId", selected.getSchemaId());
            fixed.put("topic", selected.getTopic());
            fixed.put("topicVersion", selected.getVersion());
            normalizePlan(fixed);
            ObjectNode preview;
            try {
                preview = equations.compute(selected.getDefinition(), fixed, json.createObjectNode());
            } catch (ApiException second) {
                throw planFailure(second);
            }
            response.set("simulationSpec", fixed);
            for (String field : java.util.List.of("explanation", "defaults"))
                if (retry.has(field))
                    response.set(field, retry.get(field));
            return preview;
        }
    }

    /**
     * The plan must contain every object the planner itself read from the description: the
     * non-contextual requiredObjects (with their counts) are compared with the computed
     * participants. Fewer participants than counted objects means objects would silently be
     * missing from the simulation, so the planner gets one chance to add them (it may keep the
     * plan when one capability computes several objects together).
     */
    private void reconcileParticipantCount(String description, SchemaVersion selected, ObjectNode response) {
        JsonNode spec = response.path("simulationSpec");
        int declared = 0;
        for (JsonNode object : spec.path("requiredObjects"))
            if (!object.path("contextual").asBoolean(false))
                declared += Math.max(1, object.path("count").asInt(1));
        int bound = spec.path("physicsModels").size();
        if (bound == 0 || declared <= bound)
            return;
        ObjectNode feedback = json.createObjectNode();
        feedback.put("error", "requiredObjects lists " + declared + " computed objects but physicsModels has only " + bound
                + " participant(s). Give every counted object its own physicsModels entry (distinct id and label, shared"
                + " parameters are fine) unless one capability computes several of them together, and update the explanation.");
        feedback.set("previousPlan", spec);
        JsonNode retry = askLlm(description, selected, feedback);
        JsonNode fixed = retry.path("simulationSpec");
        if (!fixed.isObject() || !"EXPLAIN".equals(retry.path("status").asText(retry.path("stage").asText()))
                || fixed.path("physicsModels").size() <= bound)
            return;
        ObjectNode copy = (ObjectNode) fixed.deepCopy();
        copy.put("schemaId", selected.getSchemaId());
        copy.put("topic", selected.getTopic());
        copy.put("topicVersion", selected.getVersion());
        response.set("simulationSpec", copy);
        for (String field : java.util.List.of("explanation", "defaults"))
            if (retry.has(field))
                response.set(field, retry.get(field));
    }

    private ApiException planFailure(ApiException cause) {
        return ApiException.unprocessable("Chưa dựng được mô hình tính toán từ mô tả này. Hãy mô tả rõ hơn tình huống hoặc thử lại. (Chi tiết: "
                        + cause.getMessage() + ")");
    }

    /**
     * Intent-preserving fixes for common plan inconsistencies (no topic knowledge
     * involved).
     */
    private void normalizePlan(ObjectNode spec) {
        if (spec == null || spec.isMissingNode())
            return;
        Map<String, ObjectNode> parameters = new LinkedHashMap<>();
        for (JsonNode parameter : spec.path("parameters")) {
            if (!(parameter instanceof ObjectNode p))
                continue;
            parameters.put(p.path("name").asText(), p);
            double value = p.path("value").asDouble(Double.NaN);
            if (Double.isFinite(value)) {
                if (p.has("min") && p.path("min").asDouble() > value)
                    p.put("min", value);
                if (p.has("max") && p.path("max").asDouble() < value)
                    p.put("max", value);
            }
        }
        double duration = spec.path("durationSeconds").asDouble(Double.NaN);
        String key = spec.path("durationParameter").asText("");
        if (spec.has("durationParameter") && (spec.path("durationParameter").isNull() || key.isBlank()))
            spec.remove("durationParameter");
        else if (!key.isBlank()) {
            ObjectNode named = parameters.get(key);
            if (named == null || !"s".equals(named.path("unit").asText())) {
                ObjectNode match = null;
                for (ObjectNode p : parameters.values())
                    if ("s".equals(p.path("unit").asText()) && p.path("value").asDouble(Double.NaN) == duration)
                        match = p;
                if (match != null)
                    spec.put("durationParameter", match.path("name").asText());
                else
                    spec.remove("durationParameter");
            } else if (!Double.isFinite(duration) || duration <= 0) {
                spec.put("durationSeconds", named.path("value").asDouble());
            } else if (named.path("value").asDouble() != duration) {
                named.put("value", duration);
                if (named.path("max").asDouble(Double.MAX_VALUE) < duration)
                    named.put("max", duration);
                if (named.path("min").asDouble(0) > duration)
                    named.put("min", duration);
            }
        }
    }

    private JsonNode askLlm(String description, SchemaVersion selected) {
        return askLlm(description, selected, null);
    }

    /**
     * The planner sees only the selected topic, and only what it needs to bind a
     * plan:
     * capability contracts (inputs/outputs/equations/assumptions) plus a key →
     * label map.
     * Laws, relations, unit catalog and curriculum are derivable or irrelevant, so
     * they are dropped.
     */
    private JsonNode planningView(JsonNode definition) {
        ObjectNode view = (ObjectNode) definition.deepCopy();
        for (String field : java.util.List.of("coreTypeRefs", "simulationCapability", "visualCapability", "limitations",
                "laws", "relationTypes", "unitCatalog", "curriculum", "metaSchemaVersion"))
            view.remove(field);
        ObjectNode labels = json.createObjectNode();
        for (JsonNode quantity : definition.path("quantityDefinitions"))
            labels.put(quantity.path("key").asText(), quantity.path("label").asText());
        view.set("quantityDefinitions", labels);
        for (JsonNode type : view.path("objectTypes"))
            if (type instanceof ObjectNode t)
                t.remove("description");
        for (JsonNode capability : view.path("capabilities")) {
            if (capability instanceof ObjectNode c)
                for (String field : java.util.List.of("execution", "validation", "rendererBindings", "validityDomain",
                        "applicability"))
                    c.remove(field);
        }
        return view;
    }

    private JsonNode askLlm(String description, SchemaVersion selected, JsonNode planFeedback) {
        ObjectNode input = json.createObjectNode();
        input.put("description", description);
        input.set("selectedSchema", planningView(selected.getDefinition()));
        if (planFeedback != null)
            input.set("planFeedback", planFeedback);
        JsonNode result = client.text(input, () -> resource("prompts/simulation-understanding-system.txt")
                + resource("prompts/simulation-understanding-response-schema.json"));
        if (result == null || !result.isObject() || !result.path("status").isTextual())
            throw ApiException.upstream("LLM returned an invalid simulation understanding response");
        return result;
    }

    private String resource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Required simulation contract is unavailable: " + path, ex);
        }
    }

    private JsonNode jsonResource(String path) {
        try {
            return json.readTree(resource(path));
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid simulation response contract: " + path, ex);
        }
    }

    /**
     * Semantic description of each solver field ("participant.output") taken from
     * the
     * approved schema: capability output units, quantity labels and renderer roles.
     * Generic over the signed physicsModels; no lesson- or object-specific
     * branches.
     */
    private ObjectNode solverFieldMeta(JsonNode definition, JsonNode brief) {
        ObjectNode result = json.createObjectNode();
        Map<String, JsonNode> quantities = new LinkedHashMap<>();
        for (JsonNode quantity : definition.path("quantityDefinitions"))
            quantities.put(quantity.path("key").asText(), quantity);
        for (JsonNode model : brief.path("physicsModels")) {
            String id = model.path("id").asText();
            JsonNode capability = null;
            for (JsonNode candidate : definition.path("capabilities"))
                if (candidate.path("capabilityId").asText().equals(model.path("capabilityId").asText()))
                    capability = candidate;
            if (id.isBlank() || capability == null)
                continue;
            Map<String, String> roles = new LinkedHashMap<>();
            for (JsonNode binding : capability.path("rendererBindings")) {
                String source = binding.path("source").asText();
                roles.put(source.substring(source.lastIndexOf('.') + 1), binding.path("role").asText());
            }
            for (JsonNode output : capability.path("outputs")) {
                String key = output.path("key").asText();
                ObjectNode field = result.putObject(id + "." + key);
                field.put("participantId", id);
                field.put("participantLabel", model.path("label").asText(id));
                field.put("quantity", key);
                field.put("unit", output.path("unit").asText(""));
                JsonNode quantity = quantities.get(key);
                if (quantity != null)
                    field.put("label", quantity.path("label").asText(key));
                String role = roles.get(key);
                if (role != null)
                    field.put("rendererRole", role);
            }
        }
        return result;
    }

    /**
     * Which value feeds each input of the law, derived from the signed plan itself
     * (not from model
     * prose): the quantity name comes from the approved vocabulary, the source is
     * the slider the value
     * is read from, a fixed number, or the law's own default. Lets the teacher see
     * a wrong binding
     * (e.g. a "half-life" slider feeding a decay constant) before confirming.
     */
    private ArrayNode formulaBindings(JsonNode definition, JsonNode capability, JsonNode model, JsonNode spec) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (JsonNode quantity : definition.path("quantityDefinitions"))
            labels.put(quantity.path("key").asText(), quantity.path("label").asText());
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        for (JsonNode parameter : spec.path("parameters"))
            parameters.put(parameter.path("name").asText(), parameter);
        ArrayNode rows = json.createArrayNode();
        for (JsonNode input : capability.path("canonicalInputs")) {
            String key = input.path("key").asText();
            ObjectNode row = rows.addObject();
            row.put("quantity", key);
            row.put("label", labels.getOrDefault(key, key));
            row.put("unit", input.path("unit").asText(""));
            JsonNode binding = model.path("inputs").path(key);
            if (binding.isTextual() && parameters.containsKey(binding.asText())) {
                JsonNode parameter = parameters.get(binding.asText());
                row.put("source", "PARAMETER");
                row.put("parameter", binding.asText());
                row.put("parameterLabel", parameter.path("label").asText(binding.asText()));
                row.set("value", parameter.path("value"));
            } else if (binding.isNumber()) {
                row.put("source", "FIXED");
                row.set("value", binding);
            } else {
                row.put("source", "DEFAULT");
                row.set("value", input.path("defaultValue"));
            }
        }
        return rows;
    }

    /**
     * What the illustrator needs from the signed plan: participants, adjustable
     * parameters, duration.
     */
    private ObjectNode visualBrief(JsonNode brief) {
        ObjectNode result = json.createObjectNode();
        result.set("durationSeconds", brief.path("durationSeconds"));
        ArrayNode participants = result.putArray("participants");
        for (JsonNode model : brief.path("physicsModels")) {
            ObjectNode item = participants.addObject();
            item.put("id", model.path("id").asText());
            item.put("label", model.path("label").asText(model.path("id").asText()));
        }
        ArrayNode parameters = result.putArray("parameters");
        for (JsonNode parameter : brief.path("parameters")) {
            ObjectNode item = parameters.addObject();
            for (String field : java.util.List.of("name", "label", "value", "unit", "min", "max"))
                if (parameter.has(field))
                    item.set(field, parameter.get(field));
        }
        return result;
    }

    /**
     * One entry per solver field: meaning (label, unit, participant, renderer role)
     * plus its first
     * value and range over the run. Keys are exactly those the program reads from
     * frame.fields.
     */
    private ObjectNode visualFields(ObjectNode fieldMeta, JsonNode frames) {
        ObjectNode result = json.createObjectNode();
        for (JsonNode frame : frames) {
            frame.path("values").fields().forEachRemaining(value -> {
                ObjectNode field = result.has(value.getKey()) ? (ObjectNode) result.get(value.getKey())
                        : result.putObject(value.getKey());
                if (!field.has("label") && fieldMeta.has(value.getKey())) {
                    fieldMeta.get(value.getKey()).fields().forEachRemaining(meta -> {
                        if (!meta.getKey().equals("quantity") && !meta.getKey().equals("participantLabel"))
                            field.set(meta.getKey(), meta.getValue());
                    });
                }
                double v = value.getValue().asDouble();
                if (!field.has("start"))
                    field.put("start", v);
                field.put("min", Math.min(field.path("min").asDouble(v), v));
                field.put("max", Math.max(field.path("max").asDouble(v), v));
            });
        }
        return result;
    }

    /**
     * Participants nothing on stage illustrates: no scene body, no scene instrument
     * on one of its fields
     * and no reference to "<id>" in the program. Programs that iterate
     * api.scene.participants bind every participant.
     */
    private static java.util.List<String> unboundParticipants(JsonNode program, JsonNode brief) {
        String code = program.path("code").asText("");
        java.util.Set<String> bodies = new java.util.HashSet<>();
        program.path("scene").path("bodies").forEach(body -> {
            if (!body.path("svg").asText("").isBlank())
                bodies.add(body.path("id").asText());
        });
        program.path("scene").path("instruments").forEach(instrument -> {
            String field = instrument.path("field").asText("");
            if (field.indexOf('.') > 0 && !instrument.path("svg").asText("").isBlank())
                bodies.add(field.substring(0, field.indexOf('.')));
        });
        boolean generic = code.contains(".participants");
        java.util.List<String> unbound = new java.util.ArrayList<>();
        for (JsonNode model : brief.path("physicsModels")) {
            String id = model.path("id").asText();
            if (id.isBlank() || bodies.contains(id) || generic)
                continue;
            if (!code.contains("'" + id + ".") && !code.contains("\"" + id + ".") && !code.contains("`" + id + ".")
                    && !code.contains("'" + id + "'") && !code.contains("\"" + id + "\""))
                unbound.add(id);
        }
        return unbound;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Routing needs only what distinguishes one topic from another: its name,
     * grade, the
     * curriculum description written for routing, the curriculum contents and the
     * titles of
     * the laws it can compute. Equation ASTs, units and vocabulary never reach the
     * router.
     */
    private String schemaDescription(SchemaVersion schema, int characterBudget) {
        JsonNode definition = schema.getDefinition();
        StringBuilder summary = new StringBuilder();
        appendBounded(summary, schema.getName(), characterBudget);
        if (definition.hasNonNull("grade"))
            appendBounded(summary, "(lớp " + definition.path("grade").asText() + ").", characterBudget);
        appendBounded(summary, definition.path("description").asText(""), characterBudget);
        for (JsonNode content : definition.path("curriculum"))
            appendBounded(summary, content.path("name").asText("") + ": " + content.path("summary").asText(""),
                    characterBudget);
        StringBuilder laws = new StringBuilder();
        for (JsonNode capability : definition.path("capabilities")) {
            String title = capability.path("title").asText("");
            if (!title.isBlank())
                laws.append(laws.isEmpty() ? "Tính được: " : "; ").append(title);
        }
        appendBounded(summary, laws.toString(), characterBudget);
        return summary.toString();
    }

    private void appendBounded(StringBuilder target, String value, int characterBudget) {
        if (value == null || value.isBlank() || target.length() >= characterBudget)
            return;
        if (!target.isEmpty())
            target.append(' ');
        int remaining = characterBudget - target.length();
        target.append(value, 0, Math.min(value.length(), remaining));
    }

    private String runnerUp(JsonNode probabilities, String selectedId) {
        String best = "";
        double bestValue = -1;
        var fields = probabilities.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getKey().equals(selectedId) && field.getValue().asDouble(0) > bestValue) {
                best = field.getKey();
                bestValue = field.getValue().asDouble(0);
            }
        }
        return best;
    }

    private double margin(JsonNode probabilities, String selectedId) {
        double selected = probabilities.path(selectedId).asDouble(0);
        double second = 0;
        var fields = probabilities.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getKey().equals(selectedId))
                second = Math.max(second, field.getValue().asDouble(0));
        }
        return selected - second;
    }


}

