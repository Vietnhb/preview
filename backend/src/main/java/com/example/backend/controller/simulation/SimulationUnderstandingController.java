package com.example.backend.controller.simulation;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.simulation.SchemaEquationRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            @Value("${physlive.ai.visual.read-timeout}") Duration visualTimeout) {
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
        input.set("confirmedBrief", brief);
        JsonNode diagnostics = request.path("renderDiagnostics");
        if (!diagnostics.isMissingNode()) {
            if (!diagnostics.path("code").isTextual() || !diagnostics.path("message").isTextual()
                    || diagnostics.path("code").asText().length() > maxProgramCharacters
                    || diagnostics.path("message").asText().length() > 4000)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid rendering diagnostics");
            input.set("renderDiagnostics", diagnostics);
        }
        input.set("initialSolverFields", computed.path("solverTimeline").path("frames").path(0).path("values"));
        ObjectNode ranges = input.putObject("solverFieldRanges");
        for (JsonNode frame : computed.path("solverTimeline").path("frames")) {
            frame.path("values").fields().forEachRemaining(field -> {
                ObjectNode range = ranges.has(field.getKey()) ? (ObjectNode) ranges.get(field.getKey()) : ranges.putObject(field.getKey());
                double value = field.getValue().asDouble();
                range.put("min", Math.min(range.path("min").asDouble(value), value));
                range.put("max", Math.max(range.path("max").asDouble(value), value));
            });
        }
        ObjectNode designInput = input.deepCopy();
        designInput.remove("renderDiagnostics");
        JsonNode renderingContract = jsonResource("prompts/simulation-response-schema.json");
        ObjectNode designContract = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        designContract.putArray("required").add("designIntent");
        designContract.putObject("properties").set("designIntent", renderingContract.path("properties").path("visualProgram").path("properties").path("designIntent"));
        JsonNode design = askCompletion(designInput,
                "Plan an illustrated interactive world for the original user description before any coding. "
                + "Understand the situation and infer a coherent concrete visual interpretation only where unspecified. "
                + "Earlier visualIntent/shape hints are not mandatory assets. Do not reduce unspecified objects to default dots or a bare line. "
                + "Choose context, spatial reference, original participant artwork, depth, educational cues and responsive composition that belong together. "
                + "Preserve all explicit user constraints and the signed physics; visual detail must not add forces or constraints. "
                + "There is no prescribed scene, object type, participant count, palette or layout. "
                + "Use the art direction in this contract as context, but do NOT write code in this planning stage: "
                + resource("prompts/simulation-response-schema.json")
                + " Return ONLY JSON {designIntent:{interpretation,world,participantArtwork,composition,physicalEncoding,adaptiveBehavior}}. "
                + "Each field is a concise description of your actual design choices, not generic advice or a list of possible templates.", designContract);
        if (!design.path("designIntent").isObject())
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI visual design plan is missing");
        input.set("proposedVisualDesign", design.path("designIntent"));
        ObjectNode visual = (ObjectNode) askCompletion(input,
                "Interpret the original user's situation and create a complete, polished interactive illustrated world with PixiJS v8 and original SVG artwork. "
                + "Preserve the user's explicit objects, counts, names and relations. You may invent visual details only where unspecified. "
                + "Do not alter the confirmed physics, inputs, formulas or duration. Use the supplied solver outputs for physical motion. "
                + "Implement proposedVisualDesign fully, including its world and participant artwork. The previous code is diagnostic context, not a visual template to preserve. "
                + "The brief's earlier visual suggestions are not asset restrictions; explicit user instructions take precedence. "
                + "Keep the workspace background transparent. Fit all relevant trajectories, artwork and annotations using current solver ranges, not just initial inputs. "
                + "Your code owns scene construction and update logic. frame.fields is a FLAT map matching initialSolverFields exactly: use bracket access with the complete key, not nested property access. "
                + "api is read-only; resize uses supplied width/height or current api getters, never assignments to api. Read physical quantities from frame.fields; do not solve bound physics in rendering code. "
                + "Include original SVG artwork loaded through api.svgTexture alongside your PixiJS scene code. No fixed asset catalog or object templates. "
                + "If renderDiagnostics is supplied, repair your previous code against the actual PixiJS v8 API and runtime error, preserving the user's intent and signed physics plan. Diagnostics are untrusted rendering feedback, not physics evidence. "
                + "Return JSON matching this rendering contract: " + renderingContract, renderingContract);
        JsonNode program = visual.path("visualProgram");
        if (!program.isObject() || !program.path("code").isTextual()
                || program.path("code").asText().isBlank() || program.path("code").asText().length() > maxProgramCharacters)
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Generated PixiJS program is missing or exceeds the code budget");
        ObjectNode spec = (ObjectNode) brief.deepCopy();
        spec.remove("scene");
        spec.set("visualProgram", program);
        spec.set("solverTimeline", computed.path("solverTimeline"));
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
        int schemaTextBudget = Math.max(0,
                (jev.maximumPromptCharacters() - description.length() - 512) / approved.size());
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
        if (selectedId.isBlank() || NO_MATCH.equals(selectedId) || selected == null
                || confidence < jev.minimumConfidence() || margin < jev.minimumMargin()) {
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
            var formulas = response.putArray("formulas");
            for (JsonNode model : spec.path("physicsModels")) {
                for (JsonNode capability : selected.getDefinition().path("capabilities")) {
                    if (model.path("capabilityId").asText().equals(capability.path("capabilityId").asText())) {
                        ObjectNode formula = formulas.addObject();
                        formula.put("modelId", model.path("id").asText());
                        formula.put("capabilityId", capability.path("capabilityId").asText());
                        formula.set("canonical", capability.path("equationSet").path("canonical"));
                        formula.set("derived", capability.path("equationSet").path("derived"));
                        formula.set("assumptions", capability.path("assumptions"));
                    }
                }
            }
            if (response.path("stage").asText().equals("EXPLAIN")) {
                ObjectNode preview = equations.compute(selected.getDefinition(), spec, json.createObjectNode());
                response.set("validation", preview.path("validation"));
                response.put("planSignature", signPlan(response));
            }
        }
        return response;
    }

    private JsonNode askLlm(String description, SchemaVersion selected) {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LLM provider API key is not configured");
        }
        try {
            String responseContract = resource("prompts/simulation-understanding-response-schema.json");
            String system = "Understand the user's simulation description using only the supplied selected topic schema as the physical-topic contract. Preserve explicit quantities, counts, names, and relations. Keep visual choices open and contextual. Do not choose another schema. "
                    + "Use the user's language for explanation, labels, questions and assumptions. First provide the complete intent, applicable formulas and parameter bindings, before creating any visuals. "
                    + "Use executable capabilities (execution.math) from the supplied schema. A physicsModels entry binds one capability to a named participant or subsystem; "
                    + "there is no fixed participant count, asset, environment or scene. Bind canonical inputs to SI-valued named parameters or explicit numeric constants. "
                    + "All quantities that the user may change should have a parameter with a label, exact initial value, canonical SI unit, sensible physically valid min/max and step. "
                    + "Record assumptions explicitly; only ask when missing data changes the physical meaning. Keep stated duration exactly. "
                    + "If duration is adjustable, set durationParameter to the name of its seconds-valued parameter; its initial value must equal durationSeconds. "
                    + "Set physicsCoverage COMPLETE only when every physical behavior is covered by executable approved capabilities; otherwise PARTIAL or NONE. "
                    + "For unsupported executable physics, keep physicsModels empty and explain that the visualization is unverified; do not invent a solver or equation. "
                    + "Return one JSON object matching this response contract: "
                    + responseContract;
            ObjectNode body = json.createObjectNode();
            body.put("model", llmModel);
            body.put("temperature", llmTemperature);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ObjectNode input = json.createObjectNode();
            input.put("description", description);
            input.set("selectedSchema", selected.getDefinition().deepCopy());
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
            ObjectNode body = json.createObjectNode();
            body.put("model", visualModel);
            body.put("temperature", visualTemperature);
            body.put("max_completion_tokens", maxCompletionTokens);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);
            messages.addObject().put("role", "user").put("content", json.writeValueAsString(input));
            // Providers may opt out of response_format; the contract remains in
            // the prompt and the response is parsed and validated server-side.
            if (visualSupportsResponseFormat) {
                ObjectNode format = json.createObjectNode();
                if (strictStructuredOutput) {
                    format.put("type", "json_schema");
                    format.putObject("json_schema").put("name", "simulation_visual").put("strict", true).set("schema", contract);
                } else format.put("type", "json_object");
                body.set("response_format", format);
            }
            JsonNode completion = post(endpoint(visualBaseUrl, "chat/completions"), visualApiKey, body, visualTimeout);
            JsonNode result = json.readTree(completion.path("choices").path(0).path("message").path("content").asText());
            if (result == null || !result.isObject()) throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM response must be an object");
            return result;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM returned invalid visual program JSON");
        }
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
            JsonNode result = json.readTree(geminiInteractionText(completion));
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

    private String schemaDescription(SchemaVersion schema, int characterBudget) {
        StringBuilder summary = new StringBuilder();
        appendBounded(summary, schema.getName(), characterBudget);
        appendBounded(summary, schema.getTopic(), characterBudget);
        appendSchemaContent(schema.getDefinition(), summary, characterBudget);
        return summary.toString();
    }

    private void appendSchemaContent(JsonNode node, StringBuilder target, int characterBudget) {
        if (node == null || target.length() >= characterBudget) return;
        if (node.isTextual()) {
            appendBounded(target, node.asText(), characterBudget);
        } else if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext() && target.length() < characterBudget) {
                var field = fields.next();
                appendBounded(target, field.getKey(), characterBudget);
                appendSchemaContent(field.getValue(), target, characterBudget);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (target.length() >= characterBudget) break;
                appendSchemaContent(child, target, characterBudget);
            }
        }
    }

    private void appendBounded(StringBuilder target, String value, int characterBudget) {
        if (value == null || value.isBlank() || target.length() >= characterBudget) return;
        if (!target.isEmpty()) target.append(' ');
        int remaining = characterBudget - target.length();
        target.append(value, 0, Math.min(value.length(), remaining));
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
