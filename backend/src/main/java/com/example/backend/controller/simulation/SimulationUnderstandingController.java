package com.example.backend.controller.simulation;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.simulation.SchemaEquationRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulation")
public class SimulationUnderstandingController {
    private static final String NO_MATCH = "NO_MATCH";
    private final SchemaDefinitionService schemas;
    private final SchemaEquationRuntime equations;
    private static final int ROUTING_SUMMARY_MAX = 1400;
    private final JevProperties jev;
    private final ObjectMapper json;
    private final HttpClient http;
    private final String llmApiKey;
    private final URI llmBaseUrl;
    private final String llmModel;
    private final String visionModel;
    private final String ocrPromptResource;
    private final double llmTemperature;
    private final Duration llmTimeout;
    private final long maxImageBytes;
    private final Set<String> allowedImageTypes;
    private final byte[] signingKey;
    private final int maxProgramCharacters;
    private final boolean strictStructuredOutput;
    private final int maxCompletionTokens;
    private final boolean visualSupportsResponseFormat;
    private final String visualApiKey;
    private final String visualProvider;
    private final URI visualBaseUrl;
    private final String visualModel;
    private final double visualTemperature;
    private final Duration visualTimeout;
    private final String visualReasoningEffort;

    public SimulationUnderstandingController(SchemaDefinitionService schemas, SchemaEquationRuntime equations, JevProperties jev,
            ObjectMapper json, @Value("${physlive.ai.provider.api-key}") String llmApiKey,
            @Value("${physlive.ai.provider.base-url}") URI llmBaseUrl,
            @Value("${physlive.ai.provider.text-model}") String llmModel,
            @Value("${physlive.ai.provider.vision-model}") String visionModel,
            @Value("${physlive.ai.provider.ocr-prompt-resource}") String ocrPromptResource,
            @Value("${physlive.ai.provider.connect-timeout}") Duration connectTimeout,
            @Value("${physlive.ai.provider.read-timeout}") Duration llmTimeout,
            @Value("${physlive.ai.provider.understanding-temperature}") double llmTemperature,
            @Value("${physlive.upload.max-image-bytes}") long maxImageBytes,
            @Value("${physlive.upload.allowed-image-types}") String allowedImageTypes,
            @Value("${jwt.secret}") String signingKey,
            @Value("${physlive.simulation.runtime.max-program-part-characters}") int maxProgramCharacters,
            @Value("${physlive.ai.visual.strict-structured-output}") boolean strictStructuredOutput,
            @Value("${physlive.ai.visual.supports-response-format}") boolean visualSupportsResponseFormat,
            @Value("${physlive.ai.visual.max-completion-tokens}") int maxCompletionTokens,
            @Value("${physlive.ai.visual.api-key}") String visualApiKey,
            @Value("${physlive.ai.visual.provider}") String visualProvider,
            @Value("${physlive.ai.visual.base-url}") URI visualBaseUrl,
            @Value("${physlive.ai.visual.model}") String visualModel,
            @Value("${physlive.ai.visual.temperature}") double visualTemperature,
            @Value("${physlive.ai.visual.read-timeout}") Duration visualTimeout,
            @Value("${physlive.ai.visual.reasoning-effort:}") String visualReasoningEffort) {
        this.schemas = schemas;
        this.equations = equations;
        this.jev = jev;
        this.json = json;
        this.llmApiKey = llmApiKey;
        this.llmBaseUrl = llmBaseUrl;
        this.llmModel = llmModel;
        this.visionModel = visionModel;
        this.ocrPromptResource = ocrPromptResource;
        this.llmTemperature = llmTemperature;
        this.llmTimeout = llmTimeout;
        this.maxImageBytes = maxImageBytes;
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        this.maxProgramCharacters = maxProgramCharacters;
        this.strictStructuredOutput = strictStructuredOutput;
        this.visualSupportsResponseFormat = visualSupportsResponseFormat;
        this.maxCompletionTokens = maxCompletionTokens;
        this.visualApiKey = visualApiKey;
        this.visualProvider = visualProvider == null || visualProvider.isBlank() ? "openai_compatible" : visualProvider.trim();
        this.visualBaseUrl = visualBaseUrl;
        this.visualModel = visualModel;
        this.visualTemperature = visualTemperature;
        this.visualTimeout = visualTimeout;
        this.visualReasoningEffort = visualReasoningEffort == null ? "" : visualReasoningEffort.trim();
        this.allowedImageTypes = java.util.Arrays.stream(allowedImageTypes.split(","))
                .map(String::trim).filter(type -> !type.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @PostMapping(path = "/understand", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode understandText(@Valid @RequestBody UnderstandRequest request) {
        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            String description = request.correctedText() == null || request.correctedText().isBlank()
                    ? request.recognizedText() : request.correctedText();
            if (description == null || description.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Confirmed OCR text is required");
            }
            return understand(description, request.sessionId());
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Simulation description is required");
        }
        return understand(request.description(), UUID.randomUUID().toString());
    }

    @PostMapping(path = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode generate(@RequestBody JsonNode request) {
        requireSignedPlan(request);
        SchemaVersion schema = selectedSchema(request);
        JsonNode brief = request.path("simulationSpec");
        ObjectNode computed = equations.compute(schema.getDefinition(), brief, json.createObjectNode());
        ObjectNode input = json.createObjectNode();
        input.put("description", request.path("description").asText());
        // The planner's plain-language explanation carries the teaching intent (what to notice).
        if (request.path("explanation").isTextual()) input.put("planExplanation", request.path("explanation").asText());
        input.set("confirmedBrief", visualBrief(brief));
        JsonNode diagnostics = request.path("renderDiagnostics");
        if (!diagnostics.isMissingNode()) {
            if (!diagnostics.path("code").isTextual() || !diagnostics.path("message").isTextual()
                    || diagnostics.path("code").asText().length() > 3 * maxProgramCharacters
                    || diagnostics.path("message").asText().length() > 4000)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid rendering diagnostics");
            input.set("renderDiagnostics", diagnostics);
        }
        ObjectNode fieldMeta = solverFieldMeta(schema.getDefinition(), brief);
        input.set("solverFields", visualFields(fieldMeta, computed.path("solverTimeline").path("frames")));
        JsonNode renderingContract = jsonResource("prompts/simulation-response-schema.json");
        ObjectNode visual = (ObjectNode) askCompletion(input,
                resource("prompts/simulation-visual-system.txt"),
                renderingContract);
        JsonNode program = visual.path("visualProgram");
        java.util.List<String> unbound = unboundParticipants(program, brief);
        if (!unbound.isEmpty()) {
            // Validation step 1 (generic, data-bound): every participant the solver computes must be
            // driven on stage by its solver fields. Give the director one chance to fix it.
            ObjectNode retryDiagnostics = input.putObject("renderDiagnostics");
            retryDiagnostics.put("code", truncate(program.toString(), 3 * maxProgramCharacters));
            retryDiagnostics.put("message", "Not bound to solver data: participant(s) " + String.join(", ", unbound)
                    + " have no body in scene and are never read in code (frame.fields['<id>.<quantity>']), so the learner "
                    + "cannot see their computed behaviour. Keep your design, but make every participant's changing fields "
                    + "visibly drive the stage.");
            JsonNode retried = askCompletion(input,
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Generated visual has neither SVG scene artwork nor PixiJS code");
        if (code.length() > maxProgramCharacters || (hasScene && scene.toString().length() > 2L * maxProgramCharacters))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Generated visual exceeds the code/artwork budget");
        if (!code.isEmpty() && (!code.startsWith("async function") || !code.contains("update")))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Generated PixiJS program must be an async function(PIXI, app, api) returning {update}");
        ObjectNode spec = (ObjectNode) brief.deepCopy();
        spec.remove("scene");
        spec.set("visualProgram", program);
        spec.set("solverTimeline", computed.path("solverTimeline"));
        spec.set("solverFieldMeta", fieldMeta);
        if (program.path("design").isObject()) spec.set("visualDesign", program.path("design"));
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

    @PostMapping(path = "/compute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode compute(@RequestBody JsonNode request) {
        requireSignedPlan(request);
        SchemaVersion schema = selectedSchema(request);
        ObjectNode result = equations.compute(schema.getDefinition(), request.path("simulationSpec"), request.path("parameters"));
        ((ObjectNode) result.path("validation")).put("topicVersion", schema.getVersion());
        return result;
    }

    private SchemaVersion selectedSchema(JsonNode request) {
        return schemas.requireCurrentApproved(request.path("schemaId").asText(), request.path("schemaVersion").asText());
    }

    private String signPlan(JsonNode request) {
        ObjectNode contract = json.createObjectNode();
        JsonNode spec = request.path("simulationSpec");
        for (String field : java.util.List.of("durationSeconds", "durationParameter", "parameters", "physicsModels", "physicsCoverage"))
            contract.set(field, spec.path(field));
        String payload = "physlive-simulation-plan-v1\n" + request.path("schemaId").asText() + "\n"
                + request.path("schemaVersion").asText() + "\n" + request.path("description").asText() + "\n"
                + schemas.compiledChecksum(contract);
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Cannot sign simulation plan", ex); }
    }

    private void requireSignedPlan(JsonNode request) {
        String actual = request.path("planSignature").asText();
        if (!java.security.MessageDigest.isEqual(signPlan(request).getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.CONFLICT, "The simulation plan changed; submit the revised description for understanding first");
    }

    @PostMapping(path = "/understand", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObjectNode understandImage(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String text) {
        if (file.isEmpty() || file.getSize() > maxImageBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image is empty or exceeds the configured size limit");
        }
        String mediaType = file.getContentType();
        if (mediaType == null || !allowedImageTypes.contains(mediaType.toLowerCase(java.util.Locale.ROOT))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image type is not allowed");
        }
        if (text != null && text.length() > jev.maximumQueryCharacters()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image context text is too long");
        }
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Vision provider API key is not configured");
        }
        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded image");
        }
        try {
            String dataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
            ObjectNode body = json.createObjectNode();
            body.put("model", visionModel);
            body.put("temperature", llmTemperature);
            var messages = body.putArray("messages");
            String ocrPrompt = new DefaultResourceLoader().getResource(ocrPromptResource)
                    .getContentAsString(StandardCharsets.UTF_8);
            messages.addObject().put("role", "system").put("content", ocrPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            var content = user.putArray("content");
            content.addObject().put("type", "text").put("text",
                    text == null || text.isBlank() ? "Transcribe the image." : "Transcribe the image. User context, only to help read unclear symbols: " + text);
            ObjectNode image = content.addObject();
            image.put("type", "image_url");
            image.putObject("image_url").put("url", dataUrl);
            body.set("response_format", json.createObjectNode().put("type", "json_object"));
            JsonNode completion = post(endpoint(llmBaseUrl, "chat/completions"), llmApiKey, body, llmTimeout);
            JsonNode transcription = json.readTree(completion.path("choices").path(0).path("message")
                    .path("content").asText(""));
            String recognized = transcription.path("text").asText("").trim();
            ObjectNode result = json.createObjectNode();
            result.put("sessionId", UUID.randomUUID().toString());
            result.put("stage", recognized.isEmpty() ? "RECOGNITION_FAILED" : "RECOGNITION");
            result.put("recognizedText", recognized);
            result.put("displayText", recognized);
            result.put("sourceMode", "IMAGE");
            result.putNull("confidence");
            if (recognized.isEmpty()) result.put("message", "Không nhận diện được nội dung rõ ràng từ ảnh.");
            return result;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Vision provider returned an invalid transcription response");
        }
    }

    private JsonNode understand(String description, String sessionId) {
        if (description.length() > jev.maximumQueryCharacters()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Simulation description exceeds the configured limit");
        }
        var approved = schemas.approvedSchemas();
        if (approved.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "No approved topic schema is available");
        }
        Map<String, SchemaVersion> byId = new LinkedHashMap<>();
        ObjectNode criteria = json.createObjectNode();
        // Reserve room for the JSON envelope/instructions; never spend more than
        // ROUTING_SUMMARY_MAX characters per topic even when the limit would allow it.
        int schemaTextBudget = Math.min(ROUTING_SUMMARY_MAX, Math.max(0,
                (jev.maximumPromptCharacters() - description.length() - 2048) / (approved.size() + 1) - 64));
        if (schemaTextBudget == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Simulation description leaves no room for approved schema routing context");
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
        ObjectNode routeRequest = json.createObjectNode();
        routeRequest.put("model", jev.model());
        routeRequest.put("state", description);
        routeRequest.set("questions", questions);
        if (routeRequest.toString().length() > jev.maximumPromptCharacters()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Approved schema routing context exceeds the configured JEV limit");
        }

        JsonNode route = post(endpoint(jev.baseUrl(), "systemone"), jev.apiKey(), routeRequest, jev.timeout());
        JsonNode answer = route.path("answers").path("topic_schema");
        String selectedId = answer.path("choice").asText("");
        double confidence = answer.path("confidence").asDouble(0);
        double margin = margin(answer.path("probabilities"), selectedId);
        SchemaVersion selected = byId.get(selectedId);
        // Topic schemas follow the curriculum strands and deliberately share capabilities
        // (e.g. free fall is taught in both kinematics and dynamics), so a narrow margin between
        // two real topics is not ambiguity. Only a narrow margin against NO_MATCH is.
        boolean ambiguousWithNoMatch = margin < jev.minimumMargin() && NO_MATCH.equals(runnerUp(answer.path("probabilities"), selectedId));
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
            return equations.compute(selected.getDefinition(), response.path("simulationSpec"), json.createObjectNode());
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
            } catch (ApiException second) { throw planFailure(second); }
            response.set("simulationSpec", fixed);
            for (String field : java.util.List.of("explanation", "defaults"))
                if (retry.has(field)) response.set(field, retry.get(field));
            return preview;
        }
    }

    private ApiException planFailure(ApiException cause) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Chưa dựng được mô hình tính toán từ mô tả này. Hãy mô tả rõ hơn tình huống hoặc thử lại. (Chi tiết: "
                        + cause.getMessage() + ")");
    }

    /** Intent-preserving fixes for common plan inconsistencies (no topic knowledge involved). */
    private void normalizePlan(ObjectNode spec) {
        if (spec == null || spec.isMissingNode()) return;
        Map<String, ObjectNode> parameters = new LinkedHashMap<>();
        for (JsonNode parameter : spec.path("parameters")) {
            if (!(parameter instanceof ObjectNode p)) continue;
            parameters.put(p.path("name").asText(), p);
            double value = p.path("value").asDouble(Double.NaN);
            if (Double.isFinite(value)) {
                if (p.has("min") && p.path("min").asDouble() > value) p.put("min", value);
                if (p.has("max") && p.path("max").asDouble() < value) p.put("max", value);
            }
        }
        double duration = spec.path("durationSeconds").asDouble(Double.NaN);
        String key = spec.path("durationParameter").asText("");
        if (spec.has("durationParameter") && (spec.path("durationParameter").isNull() || key.isBlank())) spec.remove("durationParameter");
        else if (!key.isBlank()) {
            ObjectNode named = parameters.get(key);
            if (named == null || !"s".equals(named.path("unit").asText())) {
                ObjectNode match = null;
                for (ObjectNode p : parameters.values())
                    if ("s".equals(p.path("unit").asText()) && p.path("value").asDouble(Double.NaN) == duration) match = p;
                if (match != null) spec.put("durationParameter", match.path("name").asText());
                else spec.remove("durationParameter");
            } else if (!Double.isFinite(duration) || duration <= 0) {
                spec.put("durationSeconds", named.path("value").asDouble());
            } else if (named.path("value").asDouble() != duration) {
                named.put("value", duration);
                if (named.path("max").asDouble(Double.MAX_VALUE) < duration) named.put("max", duration);
                if (named.path("min").asDouble(0) > duration) named.put("min", duration);
            }
        }
    }

    private JsonNode askLlm(String description, SchemaVersion selected) {
        return askLlm(description, selected, null);
    }

    /**
     * What the planner needs from a schema: vocabulary, laws and each capability's
     * inputs/outputs/equations/assumptions. Executable ASTs and verification data stay
     * server-side (they are executed, never interpreted by the model), which keeps the
     * prompt small for rate-limited providers.
     */
    /**
     * The planner sees only the selected topic, and only what it needs to bind a plan:
     * capability contracts (inputs/outputs/equations/assumptions) plus a key → label map.
     * Laws, relations, unit catalog and curriculum are derivable or irrelevant, so they are dropped.
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
            if (type instanceof ObjectNode t) t.remove("description");
        for (JsonNode capability : view.path("capabilities")) {
            if (capability instanceof ObjectNode c)
                for (String field : java.util.List.of("execution", "validation", "rendererBindings", "validityDomain", "applicability"))
                    c.remove(field);
        }
        return view;
    }

    private JsonNode askLlm(String description, SchemaVersion selected, JsonNode planFeedback) {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LLM provider API key is not configured");
        }
        try {
            String responseContract = resource("prompts/simulation-understanding-response-schema.json");
            String system = resource("prompts/simulation-understanding-system.txt") + responseContract;
            ObjectNode body = json.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", llmTemperature);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ObjectNode input = json.createObjectNode();
            input.put("description", description);
            input.set("selectedSchema", planningView(selected.getDefinition()));
            if (planFeedback != null) input.set("planFeedback", planFeedback);
            user.put("content", json.writeValueAsString(input));
            body.set("response_format", json.createObjectNode().put("type", "json_object"));
            JsonNode completion = post(endpoint(llmBaseUrl, "chat/completions"), llmApiKey, body, llmTimeout);
            String content = completion.path("choices").path(0).path("message").path("content").asText("");
            JsonNode result = json.readTree(content);
            if (result == null || !result.isObject() || !result.path("status").isTextual()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "LLM returned an invalid simulation understanding response");
            }
            return result;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not prepare the simulation understanding request");
        }
    }

    private String resource(String path) {
        try { return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8); }
        catch (IOException ex) { throw new IllegalStateException("Required simulation contract is unavailable: " + path, ex); }
    }

    private JsonNode jsonResource(String path) {
        try { return json.readTree(resource(path)); }
        catch (IOException ex) { throw new IllegalStateException("Invalid simulation response contract: " + path, ex); }
    }

    private JsonNode askCompletion(JsonNode input, String system, JsonNode contract) {
        if (visualApiKey == null || visualApiKey.isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Visual AI API key is not configured; set AI_VISUAL_API_KEY in env.local");
        if ("gemini_interactions".equalsIgnoreCase(visualProvider)) {
            return askGeminiInteraction(input, system, contract);
        }
        try {
            boolean openRouter = "openrouter".equalsIgnoreCase(visualProvider);
            ObjectNode body = json.createObjectNode();
            body.put("model", visualModel);
            body.put("temperature", visualTemperature);
            // OpenRouter normalises max_tokens across providers; reasoning tokens count toward it.
            body.put(openRouter ? "max_tokens" : "max_completion_tokens", maxCompletionTokens);
            if (!visualReasoningEffort.isEmpty()) {
                if (openRouter) body.putObject("reasoning").put("effort", visualReasoningEffort).put("exclude", true);
                else body.put("reasoning_effort", visualReasoningEffort);
            }
            // The JSON contract is written into the prompt only for requests where the provider
            // does not enforce it (json_object / plain fallback); strict json_schema requests carry
            // it once, in response_format, instead of twice.
            String withContract = system + "\n\nRESPONSE CONTRACT (JSON Schema):\n" + contract;
            String user = json.writeValueAsString(input);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", withContract);
            messages.addObject().put("role", "user").put("content", user);
            // Providers may opt out of response_format; the contract remains in
            // the prompt and the response is parsed and validated server-side.
            JsonNode completion;
            if (visualSupportsResponseFormat) {
                ObjectNode structured = body.deepCopy();
                ObjectNode format = structured.putObject("response_format");
                if (strictStructuredOutput) {
                    var strictMessages = structured.putArray("messages");
                    strictMessages.addObject().put("role", "system").put("content", system);
                    strictMessages.addObject().put("role", "user").put("content", user);
                    format.put("type", "json_schema");
                    format.putObject("json_schema").put("name", "simulation_visual").put("strict", true).set("schema", contract);
                } else format.put("type", "json_object");
                // Route only to endpoints that honour structured output (free models have many hosts).
                if (openRouter) structured.putObject("provider").put("require_parameters", true);
                try {
                    completion = post(endpoint(visualBaseUrl, "chat/completions"), visualApiKey, structured, visualTimeout);
                } catch (ApiException ex) {
                    // No endpoint accepts the structured-output parameters: fall back to the
                    // prompt-level contract, which is still parsed and validated below.
                    String message = String.valueOf(ex.getMessage());
                    if (!(message.contains("HTTP 400") || message.contains("HTTP 404") || message.contains("HTTP 422"))) throw ex;
                    completion = post(endpoint(visualBaseUrl, "chat/completions"), visualApiKey, body, visualTimeout);
                }
            } else {
                completion = post(endpoint(visualBaseUrl, "chat/completions"), visualApiKey, body, visualTimeout);
            }
            JsonNode choice = completion.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText()))
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Visual AI output was truncated; increase AI_VISUAL_MAX_COMPLETION_TOKENS or lower AI_VISUAL_REASONING_EFFORT");
            if (completion.has("error") && choice.isMissingNode())
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Visual AI provider error: "
                        + completion.path("error").path("message").asText("unknown").replace(visualApiKey, "[redacted]"));
            JsonNode result = parseModelJson(choice.path("message").path("content"));
            if (result == null || !result.isObject()) throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM response must be an object");
            return result;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM returned invalid visual program JSON");
        }
    }

    /** Decode the documented OpenAI-compatible message content and parse its JSON contract. */
    private JsonNode parseModelJson(JsonNode content) throws IOException {
        if (content == null || content.isNull())
            throw new IOException("Provider returned empty message content");
        String text;
        if (content.isTextual()) text = content.textValue();
        else if (content.isArray()) {
            StringBuilder joined = new StringBuilder();
            for (JsonNode part : content) {
                if (!part.isObject() || !part.path("text").isTextual())
                    throw new IOException("Provider returned an unsupported message content part");
                joined.append(part.path("text").textValue());
            }
            text = joined.toString();
        } else throw new IOException("Provider returned unsupported message content");
        return parseJsonObject(text);
    }

    /**
     * Providers without response_format support often wrap JSON in markdown fences
     * or add a sentence around it; accept the single outermost JSON object only.
     */
    private JsonNode parseJsonObject(String text) throws IOException {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```"))
            trimmed = trimmed.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        JsonNode result;
        try {
            result = json.readTree(trimmed);
        } catch (IOException direct) {
            int first = trimmed.indexOf('{'), last = trimmed.lastIndexOf('}');
            if (first < 0 || last <= first) throw direct;
            result = json.readTree(trimmed.substring(first, last + 1));
        }
        if (result == null || !result.isObject())
            throw new IOException("Provider response does not match the JSON object contract");
        return result;
    }

    /**
     * Semantic description of each solver field ("participant.output") taken from the
     * approved schema: capability output units, quantity labels and renderer roles.
     * Generic over the signed physicsModels; no lesson- or object-specific branches.
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
                if (candidate.path("capabilityId").asText().equals(model.path("capabilityId").asText())) capability = candidate;
            if (id.isBlank() || capability == null) continue;
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
                if (quantity != null) field.put("label", quantity.path("label").asText(key));
                String role = roles.get(key);
                if (role != null) field.put("rendererRole", role);
            }
        }
        return result;
    }

    private JsonNode askGeminiInteraction(JsonNode input, String system, JsonNode contract) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", visualModel);
            body.put("store", false);
            body.put("input", system + "\n\nUSER_INPUT_JSON:\n" + json.writeValueAsString(input));
            ObjectNode format = body.putObject("response_format");
            format.put("type", "text");
            format.put("mime_type", "application/json");
            format.set("schema", contract);
            JsonNode completion = postGeminiInteraction(visualBaseUrl, visualApiKey, body, visualTimeout);
            JsonNode result = parseJsonObject(geminiInteractionText(completion));
            if (result == null || !result.isObject()) throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM response must be an object");
            return result;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM returned invalid visual program JSON");
        }
    }

    private String geminiInteractionText(JsonNode completion) {
        JsonNode steps = completion.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                if (!"model_output".equals(step.path("type").asText())) continue;
                JsonNode content = step.path("content");
                if (!content.isArray()) continue;
                for (JsonNode part : content) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank()) return text;
                }
            }
        }
        String output = completion.path("output_text").asText("");
        if (!output.isBlank()) return output;
        throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM response did not include output text");
    }

    private JsonNode postGeminiInteraction(URI uri, String apiKey, JsonNode payload, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider API key is not configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("x-goog-api-key", apiKey)
                    .header("Api-Revision", "2026-05-20")
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = "";
                try {
                    String message = json.readTree(response.body()).path("error").path("message").asText("").replace(apiKey, "[redacted]");
                    if (!message.isBlank()) detail = ": " + message.substring(0, Math.min(400, message.length()));
                } catch (IOException ignored) {
                    // Provider errors need not be JSON; never return the raw response body.
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "AI routing/provider request failed with HTTP " + response.statusCode() + detail);
            }
            return json.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI request was interrupted");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI request could not be completed");
        }
    }

    private JsonNode post(URI uri, String apiKey, JsonNode payload, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider API key is not configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = "";
                try {
                    String message = json.readTree(response.body()).path("error").path("message").asText("").replace(apiKey, "[redacted]");
                    if (!message.isBlank()) detail = ": " + message.substring(0, Math.min(400, message.length()));
                } catch (IOException ignored) {
                    // Provider errors need not be JSON; never return the raw response body.
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "AI routing/provider request failed with HTTP " + response.statusCode() + detail);
            }
            return json.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI request was interrupted");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI request could not be completed");
        }
    }

    /** What the illustrator needs from the signed plan: participants, adjustable parameters, duration. */
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
                if (parameter.has(field)) item.set(field, parameter.get(field));
        }
        return result;
    }

    /**
     * One entry per solver field: meaning (label, unit, participant, renderer role) plus its first
     * value and range over the run. Keys are exactly those the program reads from frame.fields.
     */
    private ObjectNode visualFields(ObjectNode fieldMeta, JsonNode frames) {
        ObjectNode result = json.createObjectNode();
        for (JsonNode frame : frames) {
            frame.path("values").fields().forEachRemaining(value -> {
                ObjectNode field = result.has(value.getKey()) ? (ObjectNode) result.get(value.getKey()) : result.putObject(value.getKey());
                if (!field.has("label") && fieldMeta.has(value.getKey())) {
                    fieldMeta.get(value.getKey()).fields().forEachRemaining(meta -> {
                        if (!meta.getKey().equals("quantity") && !meta.getKey().equals("participantLabel")) field.set(meta.getKey(), meta.getValue());
                    });
                }
                double v = value.getValue().asDouble();
                if (!field.has("start")) field.put("start", v);
                field.put("min", Math.min(field.path("min").asDouble(v), v));
                field.put("max", Math.max(field.path("max").asDouble(v), v));
            });
        }
        return result;
    }

    /**
     * Participants whose solver fields nothing on stage reads: no scene body and no reference to
     * "<id>." in the program. Programs that iterate api.scene.participants bind every participant.
     */
    private static java.util.List<String> unboundParticipants(JsonNode program, JsonNode brief) {
        String code = program.path("code").asText("");
        java.util.Set<String> bodies = new java.util.HashSet<>();
        program.path("scene").path("bodies").forEach(body -> {
            if (!body.path("svg").asText("").isBlank()) bodies.add(body.path("id").asText());
        });
        boolean generic = code.contains(".participants");
        java.util.List<String> unbound = new java.util.ArrayList<>();
        for (JsonNode model : brief.path("physicsModels")) {
            String id = model.path("id").asText();
            if (id.isBlank() || bodies.contains(id) || generic) continue;
            if (!code.contains("'" + id + ".") && !code.contains("\"" + id + ".") && !code.contains("`" + id + "."))
                unbound.add(id);
        }
        return unbound;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Routing needs only what distinguishes one topic from another: its name, grade, the
     * curriculum description written for routing, the curriculum contents and the titles of
     * the laws it can compute. Equation ASTs, units and vocabulary never reach the router.
     */
    private String schemaDescription(SchemaVersion schema, int characterBudget) {
        JsonNode definition = schema.getDefinition();
        StringBuilder summary = new StringBuilder();
        appendBounded(summary, schema.getName(), characterBudget);
        if (definition.hasNonNull("grade")) appendBounded(summary, "(lớp " + definition.path("grade").asText() + ").", characterBudget);
        appendBounded(summary, definition.path("description").asText(""), characterBudget);
        for (JsonNode content : definition.path("curriculum"))
            appendBounded(summary, content.path("name").asText("") + ": " + content.path("summary").asText(""), characterBudget);
        StringBuilder laws = new StringBuilder();
        for (JsonNode capability : definition.path("capabilities")) {
            String title = capability.path("title").asText("");
            if (!title.isBlank()) laws.append(laws.isEmpty() ? "Tính được: " : "; ").append(title);
        }
        appendBounded(summary, laws.toString(), characterBudget);
        return summary.toString();
    }

    private void appendBounded(StringBuilder target, String value, int characterBudget) {
        if (value == null || value.isBlank() || target.length() >= characterBudget) return;
        if (!target.isEmpty()) target.append(' ');
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

    private URI endpoint(URI base, String path) {
        String value = base.toString().replaceAll("/+$", "");
        return URI.create(value + "/" + path);
    }

    public record UnderstandRequest(@Size(max = 20_000) String description, String sessionId,
            @Size(max = 20_000) String recognizedText, @Size(max = 20_000) String correctedText) {
    }
}
