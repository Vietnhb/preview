package com.example.backend.controller.simulation;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.service.problem.SchemaDefinitionService;
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

    public SimulationUnderstandingController(SchemaDefinitionService schemas, JevProperties jev,
            ObjectMapper json, @Value("${physlive.ai.provider.api-key}") String llmApiKey,
            @Value("${physlive.ai.provider.base-url}") URI llmBaseUrl,
            @Value("${physlive.ai.provider.text-model}") String llmModel,
            @Value("${physlive.ai.provider.vision-model}") String visionModel,
            @Value("${physlive.ai.provider.ocr-prompt-resource}") String ocrPromptResource,
            @Value("${physlive.ai.provider.connect-timeout}") Duration connectTimeout,
            @Value("${physlive.ai.provider.read-timeout}") Duration llmTimeout,
            @Value("${physlive.ai.provider.understanding-temperature}") double llmTemperature,
            @Value("${physlive.upload.max-image-bytes}") long maxImageBytes,
            @Value("${physlive.upload.allowed-image-types}") String allowedImageTypes) {
        this.schemas = schemas;
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
        JsonNode spec = response.path("simulationSpec");
        if (spec.isObject()) {
            ((ObjectNode) spec).put("schemaId", selected.getSchemaId());
            ((ObjectNode) spec).put("topic", selected.getTopic());
            ((ObjectNode) spec).put("topicVersion", selected.getVersion());
        }
        return response;
    }

    private JsonNode askLlm(String description, SchemaVersion selected) {
        if (llmApiKey == null || llmApiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LLM provider API key is not configured");
        }
        try {
            String responseContract = new ClassPathResource("prompts/simulation-understanding-response-schema.json")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            String system = "Understand the user's simulation description using only the supplied selected topic schema as the physical-topic contract. Preserve explicit quantities, counts, names, and relations. Keep visual choices open and contextual. Do not choose another schema. Return one JSON object matching this response contract: "
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
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "AI routing/provider request failed with HTTP " + response.statusCode());
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
