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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AIService.class);
    private static final String NO_MATCH = "NO_MATCH";
    /** How many times the planner may ask for the laws of further topics before it must plan. */
    private static final int MAX_TOPIC_EXTENSIONS = 2;
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
                throw ApiException.badRequest("Vui lòng xác nhận nội dung nhận dạng từ ảnh");
            }
            return understand(confirmedText, sessionId);
        }
        if (description == null || description.isBlank()) {
            throw ApiException.badRequest("Vui lòng nhập mô tả mô phỏng");
        }
        return understand(description, UUID.randomUUID().toString());
    }

    /** The backend interprets a correction; the client submits the prior contract and raw user input. */
    public JsonNode revise(JsonNode intent, String text) {
        if ("EXPLAIN".equals(intent.path("stage").asText())) requireSignedPlan(intent);
        String description = intent.path("description").asText("");
        String question = intent.path("question").asText("");
        String revised = "CLARIFY".equals(intent.path("stage").asText()) && !question.isBlank()
                ? description + "\n\nCâu hỏi đã hỏi người dùng: " + question + "\nNgười dùng trả lời: " + text
                : description + "\n\nYêu cầu bổ sung/chỉnh sửa của người dùng: " + text;
        return understandText(revised, null, null, null);
    }

    /** Runtime reports are diagnostics, never a new physics-validation result or a client-selected repair policy. */
    public void reportRenderDiagnostic(JsonNode simulation, String message) {
        requireSignedPlan(simulation);
        String code = simulation.path("simulationSpec").path("visualProgram").path("code").asText("");
        if (code.length() > maxProgramCharacters) throw ApiException.badRequest("Mã trình bày vượt quá giới hạn");
        ObjectNode diagnostic = json.createObjectNode();
        diagnostic.put("event", "SIMULATION_RENDER_FAILURE");
        diagnostic.put("sessionId", simulation.path("sessionId").asText());
        diagnostic.put("schemaId", simulation.path("schemaId").asText());
        diagnostic.put("schemaVersion", simulation.path("schemaVersion").asText());
        diagnostic.put("planSignature", simulation.path("planSignature").asText());
        diagnostic.put("sourceChecksum", schemas.compiledChecksum(json.createObjectNode().put("code", code)));
        diagnostic.put("message", message);
        log.warn("{}", diagnostic);
    }

    public ObjectNode generate(JsonNode request) {
        requireSignedPlan(request);
        SchemaVersion schema = selectedSchema(request);
        JsonNode brief = request.path("simulationSpec");
        JsonNode definition = definition(request, schema);
        ObjectNode computed = equations.compute(definition, brief, json.createObjectNode());
        ObjectNode fieldMeta = solverFieldMeta(definition, brief);
        ObjectNode input = completeVisualInput(json, request, definition, fieldMeta, computed);
        JsonNode diagnostics = request.path("renderDiagnostics");
        if (!diagnostics.isMissingNode()) {
            if (!diagnostics.path("code").isTextual() || !diagnostics.path("message").isTextual()
                    || diagnostics.path("code").asText().length() > 3 * maxProgramCharacters
                    || diagnostics.path("message").asText().length() > 4000)
                throw ApiException.badRequest("Thông tin chẩn đoán hiển thị không hợp lệ");
            input.set("renderDiagnostics", diagnostics);
        }
        JsonNode program = client.visual(input, resource("prompts/simulation-visual-system.txt"),
                jsonResource("prompts/simulation-response-schema.json")).path("visualProgram");
        // The LLM owns the presentation. The frontend executes its lifecycle without assembling or correcting a scene.
        requireVisualProgram(program, maxProgramCharacters);
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

    static void requireVisualProgram(JsonNode program, int maxCharacters) {
        if (!program.path("code").isTextual() || program.path("code").asText().isBlank())
            throw ApiException.upstream("Mô phỏng được tạo chưa có mã hình minh họa");
        if (program.path("code").asText().length() > maxCharacters)
            throw ApiException.upstream("Mã hình minh họa được tạo vượt quá giới hạn tài nguyên");
        double rate = program.path("playbackRate").asDouble(Double.NaN);
        if (!program.path("playbackRate").isNumber() || !Double.isFinite(rate) || rate <= 0 || rate < 1e-15 || rate > 1e15)
            throw ApiException.upstream("Tỉ lệ thời gian hiển thị không hợp lệ");
    }

    public ObjectNode compute(JsonNode request) {
        requireSignedPlan(request);
        SchemaVersion schema = selectedSchema(request);
        ObjectNode result = equations.compute(definition(request, schema), request.path("simulationSpec"),
                request.path("parameters"));
        ((ObjectNode) result.path("validation")).put("topicVersion", schema.getVersion());
        return result;
    }

    public LibraryItemResponse save(SimulationRequests.Save request) {
        requireSignedPlan(request.simulation());
        SchemaVersion schema = selectedSchema(request.simulation());
        ObjectNode computed = equations.compute(definition(request.simulation(), schema),
                request.simulation().path("simulationSpec"), request.parameters());
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

    /**
     * The laws a plan was made from: its own topic and every further topic the signed plan names
     * (simulationSpec.relatedSchemas), each at the version it was planned against.
     */
    private JsonNode definition(JsonNode request, SchemaVersion schema) {
        JsonNode related = request.path("simulationSpec").path("relatedSchemas");
        if (!related.isArray() || related.isEmpty())
            return schema.getDefinition();
        java.util.List<JsonNode> more = new java.util.ArrayList<>();
        for (JsonNode entry : related)
            more.add(schemas.requireCurrentApproved(entry.path("schemaId").asText(), entry.path("schemaVersion").asText())
                    .getDefinition());
        return merged(schema.getDefinition(), more);
    }

    /** One definition holding the laws and vocabulary of several topics; what the first topic defines stands. */
    static JsonNode merged(JsonNode primary, java.util.List<JsonNode> more) {
        if (more.isEmpty())
            return primary;
        ObjectNode all = primary.deepCopy();
        Map<String, String> identity = Map.of("capabilities", "capabilityId", "quantityDefinitions", "key", "objectTypes",
                "type", "unitCatalog", "symbol", "laws", "id");
        identity.forEach((list, key) -> {
            ArrayNode target = all.withArray(list);
            java.util.Set<String> present = new java.util.HashSet<>();
            target.forEach(item -> present.add(item.path(key).asText()));
            for (JsonNode definition : more)
                for (JsonNode item : definition.path(list))
                    if (present.add(item.path(key).asText()))
                        target.add(item.deepCopy());
        });
        return all;
    }

    /**
     * The plan's signature. What the learner watches is part of the confirmed plan
     * (the renderer checks the stage against it); plans signed before it was
     * (version 1) carry a signature without it.
     */
    private String signPlan(JsonNode request) {
        return signPlan(request, true);
    }

    private String signPlan(JsonNode request, boolean watched) {
        ObjectNode contract = json.createObjectNode();
        JsonNode spec = request.path("simulationSpec");
        for (String field : java.util.List.of("durationSeconds", "durationParameter", "parameters", "physicsModels",
                "physicsCoverage"))
            contract.set(field, spec.path(field));
        if (watched && spec.has("observables"))
            contract.set("observables", spec.get("observables"));
        // the further topics whose laws the plan uses; a plan made from one topic signs as before
        if (spec.path("relatedSchemas").isArray() && !spec.path("relatedSchemas").isEmpty())
            contract.set("relatedSchemas", spec.get("relatedSchemas"));
        String payload = "physlive-simulation-plan-v" + (watched ? 2 : 1) + "\n" + request.path("schemaId").asText() + "\n"
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
        byte[] actual = request.path("planSignature").asText().getBytes(StandardCharsets.UTF_8);
        if (!java.security.MessageDigest.isEqual(signPlan(request, true).getBytes(StandardCharsets.UTF_8), actual)
                && !java.security.MessageDigest.isEqual(signPlan(request, false).getBytes(StandardCharsets.UTF_8), actual))
            throw ApiException
                    .conflict("Kế hoạch mô phỏng đã thay đổi. Vui lòng gửi lại mô tả đã sửa để phân tích trước");
    }

    public ObjectNode understandImage(byte[] imageBytes, String mediaType, String text) {
        if (imageBytes == null || imageBytes.length == 0 || imageBytes.length > upload.maxImageBytes()) {
            throw ApiException.badRequest("Ảnh trống hoặc vượt quá giới hạn dung lượng");
        }
        if (mediaType == null || !upload.allowedImageTypes().contains(mediaType.toLowerCase(java.util.Locale.ROOT))) {
            throw ApiException.unsupportedMedia("Định dạng ảnh không được hỗ trợ");
        }
        if (text != null && text.length() > jev.maximumQueryCharacters()) {
            throw ApiException.badRequest("Nội dung mô tả kèm ảnh quá dài");
        }
        JsonNode transcription = client.transcribe(imageBytes, mediaType, text, () -> {
            try {
                return new DefaultResourceLoader().getResource(ai.provider().ocrPromptResource())
                        .getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw ApiException.upstream("Kết quả nhận dạng ảnh không hợp lệ");
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
            throw ApiException.badRequest("Mô tả mô phỏng vượt quá giới hạn độ dài");
        }
        var approved = schemas.approvedSchemas();
        if (approved.isEmpty()) {
            throw ApiException.unprocessable("Chưa có mô hình chủ đề được phê duyệt");
        }
        Map<String, SchemaVersion> byId = new LinkedHashMap<>();
        ObjectNode criteria = json.createObjectNode();
        // Reserve room for the JSON envelope/instructions; never spend more than
        // ROUTING_SUMMARY_MAX characters per topic even when the limit would allow it.
        int schemaTextBudget = Math.min(ROUTING_SUMMARY_MAX, Math.max(0,
                (jev.maximumPromptCharacters() - description.length() - 2048) / (approved.size() + 1) - 64));
        if (schemaTextBudget == 0) {
            throw ApiException.unprocessable("Mô tả quá dài để phân tích và chọn mô hình. Vui lòng rút gọn nội dung");
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

        java.util.List<SchemaVersion> related = new java.util.ArrayList<>();
        JsonNode plan = askLlm(description, selected, related, approved, null);
        for (int round = 0; "EXTEND".equals(stageOf(plan)); round++) {
            int known = related.size();
            for (JsonNode id : plan.path("relatedSchemas")) {
                SchemaVersion more = byId.get(id.asText());
                if (more != null && more != selected && !related.contains(more))
                    related.add(more);
            }
            if (round == MAX_TOPIC_EXTENSIONS || related.size() == known)
                throw ApiException.upstream("Kết quả phân tích mô phỏng của AI không hợp lệ");
            plan = askLlm(description, selected, related, approved, null);
        }
        ObjectNode response = plan.deepCopy();
        if (!response.hasNonNull("stage"))
            response.set("stage", response.path("status").deepCopy());
        response.put("sessionId", sessionId);
        response.put("schemaId", selected.getSchemaId());
        response.put("topic", selected.getTopic());
        response.put("schemaVersion", selected.getVersion());
        response.put("description", description);
        if (!"EXPLAIN".equals(stageOf(response)))
            return response;
        if (!response.path("simulationSpec").isObject())
            throw ApiException.upstream("Kết quả phân tích mô phỏng của AI không hợp lệ");
        response.set("simulationSpec", stamped(response.path("simulationSpec"), selected, related));
        ObjectNode preview = settlePlan(description, selected, related, approved, response);
        response.set("validation", preview.path("validation"));
        response.put("planSignature", signPlan(response, true));
        response.set("formulas", formulas(definition(response, selected), response.path("simulationSpec"),
                preview.path("solverTimeline").path("frames").path(0).path("values")));
        return response;
    }

    /**
     * One plan check: the solver computes the plan, and the plan is compared with
     * what the planner itself
     * declared in it. Problems are reported in the planner's terms so it can
     * correct them.
     */
    private record PlanCheck(ObjectNode preview, java.util.List<String> problems, ApiException solverError) {
    }

    private PlanCheck check(JsonNode definition, ObjectNode spec) {
        normalizePlan(spec);
        java.util.List<String> problems = new java.util.ArrayList<>(selfContradictions(spec, definition));
        try {
            return new PlanCheck(equations.compute(definition, spec, json.createObjectNode()), problems,
                    null);
        } catch (ApiException error) {
            problems.add(0, String.valueOf(error.getMessage()));
            return new PlanCheck(null, problems, error);
        }
    }

    /**
     * Checks the plan and, when anything is wrong, gives the planner one chance to
     * correct it. A plan the
     * solver can compute is never lost: the correction is adopted only when it
     * computes and has fewer
     * problems. Returns the solver preview of the plan that stands.
     */
    private ObjectNode settlePlan(String description, SchemaVersion selected, java.util.List<SchemaVersion> related,
            java.util.List<SchemaVersion> approved, ObjectNode response) {
        JsonNode definition = merged(selected.getDefinition(), related.stream().map(SchemaVersion::getDefinition).toList());
        PlanCheck first = check(definition, (ObjectNode) response.path("simulationSpec"));
        if (first.problems().isEmpty())
            return first.preview();
        ObjectNode feedback = json.createObjectNode();
        feedback.put("error", String.join(" ", first.problems()));
        feedback.set("previousPlan", response.path("simulationSpec"));
        JsonNode retry = first.preview() == null ? askLlm(description, selected, related, approved, feedback)
                : askLlmOptional(description, selected, related, approved, feedback);
        if (retry != null && "EXPLAIN".equals(stageOf(retry)) && retry.path("simulationSpec").isObject()) {
            ObjectNode fixed = stamped(retry.path("simulationSpec"), selected, related);
            PlanCheck second = check(definition, fixed);
            if (second.preview() != null
                    && (first.preview() == null || second.problems().size() < first.problems().size())) {
                response.set("simulationSpec", fixed);
                for (String field : java.util.List.of("explanation", "defaults"))
                    if (retry.has(field))
                        response.set(field, retry.get(field));
                return second.preview();
            }
        }
        if (first.preview() != null)
            return first.preview();
        throw ApiException.unprocessable(
                "Chưa dựng được mô hình tính toán từ mô tả này. Hãy mô tả rõ hơn tình huống hoặc thử lại. (Chi tiết: "
                        + first.solverError().getMessage() + ")");
    }

    /**
     * Where the plan contradicts itself: a watched value that no participant
     * computes, or one not tied to its object. How objects map to participants is
     * the planner's judgement, not checked here.
     */
    private java.util.List<String> selfContradictions(JsonNode spec, JsonNode definition) {
        java.util.List<String> found = new java.util.ArrayList<>();
        java.util.List<String> results = new java.util.ArrayList<>();
        for (JsonNode model : spec.path("physicsModels")) {
            JsonNode capability = capabilityOf(definition, model.path("capabilityId").asText());
            if (capability != null)
                capability.path("outputs").forEach(output -> results.add(model.path("id").asText() + "."
                        + output.path("key").asText()));
        }
        java.util.List<String> unusable = new java.util.ArrayList<>();
        for (JsonNode observable : spec.path("observables"))
            if (!results.contains(observable.path("field").asText()) || observable.path("object").asText("").isBlank()
                    || observable.path("label").asText("").isBlank())
                unusable.add("\"" + observable.path("field").asText() + "\"");
        if (!results.isEmpty() && spec.path("observables").isEmpty())
            found.add("simulationSpec.observables is empty: list the results the learner watches.");
        else if (!unusable.isEmpty())
            found.add("observables " + String.join(", ", unusable) + " must each name one computed result ("
                    + String.join(", ", results) + ") with the object it belongs to and a label.");
        return found;
    }

    /**
     * Resolves what the plan states twice (a parameter's value and its range; the
     * duration and its slider)
     * without guessing intent: the stated value wins.
     */
    private void normalizePlan(ObjectNode spec) {
        Map<String, ObjectNode> parameters = new LinkedHashMap<>();
        for (JsonNode parameter : spec.path("parameters"))
            if (parameter instanceof ObjectNode p)
                parameters.put(p.path("name").asText(), p);
        ObjectNode control = parameters.get(spec.path("durationParameter").asText(""));
        String unit = control == null ? "" : control.path("unit").asText();
        if (control == null || !equations.sameQuantity(unit, "s"))
            spec.remove("durationParameter");
        else if (spec.path("durationSeconds").asDouble(0) > 0)
            control.put("value", equations.convert(spec.path("durationSeconds").asDouble(), "s", unit));
        else
            spec.put("durationSeconds", equations.convert(control.path("value").asDouble(), unit, "s"));
        for (ObjectNode p : parameters.values()) {
            double value = p.path("value").asDouble(Double.NaN);
            if (!Double.isFinite(value))
                continue;
            if (p.has("min") && p.path("min").asDouble() > value)
                p.put("min", value);
            if (p.has("max") && p.path("max").asDouble() < value)
                p.put("max", value);
        }
    }

    /**
     * The plan as the server signs it: always tied to the topic it was planned
     * against.
     */
    private ObjectNode stamped(JsonNode spec, SchemaVersion selected, java.util.List<SchemaVersion> related) {
        ObjectNode copy = spec.deepCopy();
        copy.put("schemaId", selected.getSchemaId());
        copy.put("topic", selected.getTopic());
        copy.put("topicVersion", selected.getVersion());
        // set by the server only: the further topics whose laws were offered to the planner
        copy.remove("relatedSchemas");
        ArrayNode others = json.createArrayNode();
        for (SchemaVersion other : related)
            others.addObject().put("schemaId", other.getSchemaId()).put("schemaVersion", other.getVersion())
                    .put("topic", other.getTopic());
        if (!others.isEmpty())
            copy.set("relatedSchemas", others);
        return copy;
    }

    private static String stageOf(JsonNode result) {
        return result.path("stage").asText(result.path("status").asText());
    }

    /**
     * The approved formulas behind every participant, with the value that feeds
     * each input.
     */
    private ArrayNode formulas(JsonNode definition, JsonNode spec, JsonNode firstValues) {
        ArrayNode formulas = json.createArrayNode();
        for (JsonNode model : spec.path("physicsModels")) {
            JsonNode capability = capabilityOf(definition, model.path("capabilityId").asText());
            if (capability == null)
                continue;
            ObjectNode formula = formulas.addObject();
            formula.put("modelId", model.path("id").asText());
            formula.put("label", model.path("label").asText(model.path("id").asText()));
            formula.put("capabilityId", capability.path("capabilityId").asText());
            formula.set("canonical", capability.path("equationSet").path("canonical"));
            formula.set("derived", capability.path("equationSet").path("derived"));
            formula.set("assumptions", capability.path("assumptions"));
            formula.set("bindings", formulaBindings(definition, capability, model, spec, firstValues));
        }
        return formulas;
    }

    private static JsonNode capabilityOf(JsonNode definition, String capabilityId) {
        for (JsonNode capability : definition.path("capabilities"))
            if (capability.path("capabilityId").asText().equals(capabilityId))
                return capability;
        return null;
    }

    /**
     * What the planner needs to bind a plan, and nothing else: capability contracts
     * (inputs/outputs/equations/assumptions), a key → label map, which inputs may follow a value that changes
     * during the run, and the spellings in which each unit may be written. Laws, relations, unit catalog and
     * curriculum are derivable or irrelevant, so they are dropped.
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
        ObjectNode units = json.createObjectNode();
        for (JsonNode capability : view.path("capabilities")) {
            if (!(capability instanceof ObjectNode c))
                continue;
            java.util.Set<String> changing = equations.changingInputs(capability);
            for (JsonNode input : c.path("canonicalInputs")) {
                ((ObjectNode) input).remove("timeVarying");
                if (changing.contains(input.path("key").asText()))
                    ((ObjectNode) input).put("acceptsChanging", true);
                String unit = input.path("unit").asText();
                java.util.List<String> spellings = equations.spellings(unit);
                if (spellings.size() > 1 && !units.has(unit))
                    spellings.forEach(units.withArray(unit)::add);
            }
            for (String field : java.util.List.of("execution", "validation", "rendererBindings", "validityDomain",
                    "applicability"))
                c.remove(field);
        }
        view.set("unitSpellings", units);
        return view;
    }

    /** The approved topics the plan does not use yet, by name and by the laws each can compute. */
    private ArrayNode otherTopics(java.util.List<SchemaVersion> approved, SchemaVersion selected,
            java.util.List<SchemaVersion> related) {
        ArrayNode topics = json.createArrayNode();
        for (SchemaVersion schema : approved) {
            if (schema == selected || related.contains(schema))
                continue;
            ObjectNode topic = topics.addObject().put("schemaId", schema.getSchemaId()).put("name", schema.getName());
            for (JsonNode capability : schema.getDefinition().path("capabilities"))
                if (!capability.path("title").asText("").isBlank())
                    topic.withArray("laws").add(capability.path("title").asText());
        }
        return topics;
    }

    private JsonNode askLlm(String description, SchemaVersion selected, java.util.List<SchemaVersion> related,
            java.util.List<SchemaVersion> approved, JsonNode planFeedback) {
        ObjectNode input = json.createObjectNode();
        input.put("description", description);
        input.set("selectedSchema", planningView(merged(selected.getDefinition(),
                related.stream().map(SchemaVersion::getDefinition).toList())));
        input.set("otherTopics", otherTopics(approved, selected, related));
        if (planFeedback != null)
            input.set("planFeedback", planFeedback);
        JsonNode result = client.text(input, () -> resource("prompts/simulation-understanding-system.txt")
                + resource("prompts/simulation-understanding-response-schema.json"));
        if (result == null || !result.isObject() || !result.path("status").isTextual())
            throw ApiException.upstream("Kết quả phân tích mô phỏng của AI không hợp lệ");
        return result;
    }

    /**
     * A second opinion that only improves an already usable plan: when it cannot be
     * obtained, the plan stands.
     */
    private JsonNode askLlmOptional(String description, SchemaVersion selected, java.util.List<SchemaVersion> related,
            java.util.List<SchemaVersion> approved, JsonNode planFeedback) {
        try {
            return askLlm(description, selected, related, approved, planFeedback);
        } catch (ApiException unavailable) {
            return null;
        }
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
            JsonNode capability = capabilityOf(definition, model.path("capabilityId").asText());
            if (id.isBlank() || capability == null)
                continue;
            Map<String, String> roles = new LinkedHashMap<>();
            for (JsonNode binding : capability.path("rendererBindings"))
                roles.put(binding.path("source").asText(), binding.path("role").asText());
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
                if (quantity != null && !quantity.path("aliases").path(0).asText("").isBlank())
                    field.put("symbol", quantity.path("aliases").path(0).asText());
                String role = roles.get(key);
                if (role != null)
                    field.put("rendererRole", role);
            }
            // A value the plan fixes (an input bound to a number) can be shown beside its object as well.
            Map<String, String> units = new LinkedHashMap<>();
            capability.path("canonicalInputs").forEach(input -> units.put(input.path("key").asText(), input.path("unit").asText("")));
            model.path("inputs").fields().forEachRemaining(input -> {
                String key = id + "." + input.getKey();
                if (!input.getValue().isNumber() || result.has(key))
                    return;
                ObjectNode field = result.putObject(key);
                field.put("participantId", id);
                field.put("participantLabel", model.path("label").asText(id));
                field.put("quantity", input.getKey());
                field.put("unit", units.getOrDefault(input.getKey(), ""));
                JsonNode quantity = quantities.get(input.getKey());
                if (quantity != null)
                    field.put("label", quantity.path("label").asText(input.getKey()));
                field.set("constant", input.getValue());
            });
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
    private ArrayNode formulaBindings(JsonNode definition, JsonNode capability, JsonNode model, JsonNode spec,
            JsonNode firstValues) {
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
                row.put("unit", parameter.path("unit").asText(input.path("unit").asText("")));
                row.set("value", parameter.path("value"));
            } else if (binding.isTextual()) {
                // "<participant>.<output>": the result of another participant's approved law
                String reference = binding.asText(),
                        source = reference.substring(0, Math.max(0, reference.lastIndexOf('.')));
                row.put("source", "OUTPUT");
                row.put("participant", source);
                for (JsonNode other : spec.path("physicsModels"))
                    if (other.path("id").asText().equals(source))
                        row.put("participantLabel", other.path("label").asText(source));
                row.put("output", reference.substring(reference.lastIndexOf('.') + 1));
                row.put("outputLabel",
                        labels.getOrDefault(reference.substring(reference.lastIndexOf('.') + 1), reference));
                if (firstValues.path(reference).isNumber())
                    row.set("value", firstValues.path(reference));
                else
                    row.putNull("value");
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

    /** Preserve the signed plan and solver data without field filtering, rounding or resampling. */
    static ObjectNode completeVisualInput(ObjectMapper json, JsonNode request, JsonNode schemaDefinition,
            JsonNode fieldMeta, JsonNode computed) {
        ObjectNode input = json.createObjectNode();
        input.set("description", request.path("description").deepCopy());
        if (request.has("explanation"))
            input.set("planExplanation", request.get("explanation").deepCopy());
        input.set("confirmedPlan", request.path("simulationSpec").deepCopy());
        input.set("physicsSchema", schemaDefinition.deepCopy());
        input.set("solverFieldMeta", fieldMeta.deepCopy());
        input.set("solverTimeline", computed.path("solverTimeline").deepCopy());
        input.set("solverValidation", computed.path("validation").deepCopy());
        return input;
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
