package com.example.backend.integration.ai;

import com.example.backend.config.AIProperties;
import com.example.backend.config.JevProperties;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Provider protocols and transport; simulation planning stays in its business service. */
@Component
@lombok.extern.slf4j.Slf4j
public class AIClient {
    private final AIProperties ai;
    private final JevProperties jev;
    private final ObjectMapper json;
    private final HttpClient http;

    public AIClient(AIProperties ai, JevProperties jev, ObjectMapper json) {
        this.ai = ai;
        this.jev = jev;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(ai.provider().connectTimeout()).build();
    }

    public JsonNode route(String state, JsonNode questions) {
        ObjectNode body = json.createObjectNode();
        body.put("model", jev.model());
        body.put("state", state);
        body.set("questions", questions);
        if (body.toString().length() > jev.maximumPromptCharacters())
            throw ApiException.unprocessable("Dữ liệu chọn mô hình vượt quá giới hạn xử lý");
        return post(endpoint(jev.baseUrl(), "systemone"), jev.apiKey(), body, jev.timeout()).path("answers");
    }

    public JsonNode text(JsonNode input, Supplier<String> prompt) {
        if (ai.provider().apiKey() == null || ai.provider().apiKey().isBlank())
            throw ApiException.unavailable("Dịch vụ phân tích AI chưa được cấu hình");
        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", ai.provider().textModel());
            body.put("temperature", ai.provider().understandingTemperature());
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", prompt.get());
            messages.addObject().put("role", "user").put("content", json.writeValueAsString(input));
            body.set("response_format", json.createObjectNode().put("type", "json_object"));
            JsonNode completion = post(endpoint(ai.provider().baseUrl(), "chat/completions"), ai.provider().apiKey(),
                    body, ai.provider().readTimeout());
            return json.readTree(completion.path("choices").path(0).path("message").path("content").asText(""));
        } catch (IOException ex) {
            throw ApiException.internal("Không thể chuẩn bị yêu cầu phân tích mô phỏng");
        }
    }

    public JsonNode transcribe(byte[] bytes, String mediaType, String context, Supplier<String> prompt) {
        if (ai.provider().apiKey() == null || ai.provider().apiKey().isBlank())
            throw ApiException.unavailable("Dịch vụ nhận dạng ảnh chưa được cấu hình");
        try {
            String dataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            ObjectNode body = json.createObjectNode();
            body.put("model", ai.provider().visionModel());
            body.put("temperature", ai.provider().understandingTemperature());
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", prompt.get());
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            var content = user.putArray("content");
            content.addObject().put("type", "text").put("text", context == null || context.isBlank()
                    ? "Transcribe the image."
                    : "Transcribe the image. User context, only to help read unclear symbols: " + context);
            content.addObject().put("type", "image_url").putObject("image_url").put("url", dataUrl);
            body.set("response_format", json.createObjectNode().put("type", "json_object"));
            JsonNode completion = post(endpoint(ai.provider().baseUrl(), "chat/completions"), ai.provider().apiKey(),
                    body, ai.provider().readTimeout());
            return json.readTree(completion.path("choices").path(0).path("message").path("content").asText(""));
        } catch (IOException ex) {
            throw ApiException.upstream("Kết quả nhận dạng ảnh không hợp lệ");
        }
    }

    public JsonNode visual(JsonNode input, String system, JsonNode contract) {
        if (ai.visual().apiKey() == null || ai.visual().apiKey().isBlank())
            throw ApiException.unavailable("Dịch vụ tạo cảnh minh họa AI chưa được cấu hình. Vui lòng liên hệ quản trị viên");
        if ("gemini_interactions".equalsIgnoreCase(ai.visual().provider())) {
            return askGeminiInteraction(input, system, contract);
        }
        try {
            boolean openRouter = "openrouter".equalsIgnoreCase(ai.visual().provider());
            ObjectNode body = json.createObjectNode();
            body.put("model", ai.visual().model());
            body.put("temperature", ai.visual().temperature());
            // OpenRouter normalises max_tokens across providers; reasoning tokens count
            // toward it.
            body.put(openRouter ? "max_tokens" : "max_completion_tokens", ai.visual().maxCompletionTokens());
            if (!ai.visual().reasoningEffort().isEmpty()) {
                if (openRouter)
                    body.putObject("reasoning").put("effort", ai.visual().reasoningEffort()).put("exclude", true);
                else
                    body.put("reasoning_effort", ai.visual().reasoningEffort());
            }
            // The JSON contract is written into the prompt only for requests where the
            // provider
            // does not enforce it (json_object / plain fallback); strict json_schema
            // requests carry
            // it once, in response_format, instead of twice.
            String withContract = system + "\n\nRESPONSE CONTRACT (JSON Schema):\n" + contract;
            String user = json.writeValueAsString(input);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", withContract);
            messages.addObject().put("role", "user").put("content", user);
            // Providers may opt out of response_format; the contract remains in
            // the prompt and the response is parsed and validated server-side.
            JsonNode completion;
            if (ai.visual().supportsResponseFormat()) {
                ObjectNode structured = body.deepCopy();
                ObjectNode format = structured.putObject("response_format");
                if (ai.visual().strictStructuredOutput()) {
                    var strictMessages = structured.putArray("messages");
                    strictMessages.addObject().put("role", "system").put("content", system);
                    strictMessages.addObject().put("role", "user").put("content", user);
                    format.put("type", "json_schema");
                    format.putObject("json_schema").put("name", "simulation_visual").put("strict", true).set("schema",
                            contract);
                } else
                    format.put("type", "json_object");
                // Route only to endpoints that honour structured output (free models have many
                // hosts).
                if (openRouter)
                    structured.putObject("provider").put("require_parameters", true);
                try {
                    completion = post(endpoint(ai.visual().baseUrl(), "chat/completions"), ai.visual().apiKey(), structured,
                            ai.visual().readTimeout());
                } catch (ApiException ex) {
                    // No endpoint accepts the structured-output parameters: fall back to the
                    // prompt-level contract, which is still parsed and validated below.
                    String message = String.valueOf(ex.getMessage());
                    if (!(message.contains("HTTP 400") || message.contains("HTTP 404") || message.contains("HTTP 422")))
                        throw ex;
                    completion = post(endpoint(ai.visual().baseUrl(), "chat/completions"), ai.visual().apiKey(), body, ai.visual().readTimeout());
                }
            } else {
                completion = post(endpoint(ai.visual().baseUrl(), "chat/completions"), ai.visual().apiKey(), body, ai.visual().readTimeout());
            }
            JsonNode choice = completion.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText()))
                throw ApiException.upstream("Cảnh minh họa AI chưa được tạo đầy đủ. Vui lòng thử lại hoặc rút gọn mô tả");
            if (completion.has("error") && choice.isMissingNode()) {
                log.warn("Visual AI provider error: {}", completion.path("error").path("message")
                        .asText("unknown").replace(ai.visual().apiKey(), "[redacted]"));
                throw ApiException.upstream("Dịch vụ tạo cảnh minh họa AI gặp lỗi. Vui lòng thử lại.");
            }
            JsonNode result = parseModelJson(choice.path("message").path("content"));
            if (result == null || !result.isObject())
                throw ApiException.upstream("Phản hồi của dịch vụ AI không đúng định dạng");
            return result;
        } catch (IOException ex) {
            throw ApiException.upstream("Dữ liệu cảnh minh họa của AI không hợp lệ");
        }
    }

    /**
     * Decode the documented OpenAI-compatible message content and parse its JSON
     * contract.
     */
    private JsonNode parseModelJson(JsonNode content) throws IOException {
        if (content == null || content.isNull())
            throw new IOException("Provider returned empty message content");
        String text;
        if (content.isTextual())
            text = content.textValue();
        else if (content.isArray()) {
            StringBuilder joined = new StringBuilder();
            for (JsonNode part : content) {
                if (!part.isObject() || !part.path("text").isTextual())
                    throw new IOException("Provider returned an unsupported message content part");
                joined.append(part.path("text").textValue());
            }
            text = joined.toString();
        } else
            throw new IOException("Provider returned unsupported message content");
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
            if (first < 0 || last <= first)
                throw direct;
            result = json.readTree(trimmed.substring(first, last + 1));
        }
        if (result == null || !result.isObject())
            throw new IOException("Provider response does not match the JSON object contract");
        return result;
    }

    private JsonNode askGeminiInteraction(JsonNode input, String system, JsonNode contract) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", ai.visual().model());
            body.put("store", false);
            body.put("input", system + "\n\nUSER_INPUT_JSON:\n" + json.writeValueAsString(input));
            ObjectNode format = body.putObject("response_format");
            format.put("type", "text");
            format.put("mime_type", "application/json");
            format.set("schema", contract);
            JsonNode completion = post(ai.visual().baseUrl(), ai.visual().apiKey(), body, ai.visual().readTimeout(), true);
            JsonNode result = parseJsonObject(geminiInteractionText(completion));
            if (result == null || !result.isObject())
                throw ApiException.upstream("Phản hồi của dịch vụ AI không đúng định dạng");
            return result;
        } catch (IOException ex) {
            throw ApiException.upstream("Dữ liệu cảnh minh họa của AI không hợp lệ");
        }
    }

    private String geminiInteractionText(JsonNode completion) {
        JsonNode steps = completion.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                if (!"model_output".equals(step.path("type").asText()))
                    continue;
                JsonNode content = step.path("content");
                if (!content.isArray())
                    continue;
                for (JsonNode part : content) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank())
                        return text;
                }
            }
        }
        String output = completion.path("output_text").asText("");
        if (!output.isBlank())
            return output;
        throw ApiException.upstream("Dịch vụ AI không trả về nội dung");
    }

    private JsonNode post(URI uri, String apiKey, JsonNode payload, Duration timeout) {
        return post(uri, apiKey, payload, timeout, false);
    }

    private JsonNode post(URI uri, String apiKey, JsonNode payload, Duration timeout, boolean gemini) {
        if (apiKey == null || apiKey.isBlank())
            throw ApiException.unavailable("Dịch vụ AI chưa được cấu hình");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            if (gemini) builder.header("x-goog-api-key", apiKey).header("Api-Revision", "2026-05-20");
            else builder.header("Authorization", "Bearer " + apiKey);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = "";
                try {
                    String message = json.readTree(response.body()).path("error").path("message").asText("")
                            .replace(apiKey, "[redacted]");
                    if (!message.isBlank()) detail = ": " + message.substring(0, Math.min(400, message.length()));
                } catch (IOException ignored) {
                    // Provider errors need not be JSON; never return the raw response body.
                }
                log.warn("AI provider returned HTTP {}{}", response.statusCode(), detail);
                throw ApiException.upstream("Yêu cầu đến dịch vụ AI thất bại với mã HTTP " + response.statusCode() + ". Vui lòng thử lại.");
            }
            return json.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("Yêu cầu AI bị gián đoạn. Vui lòng thử lại");
        } catch (IOException ex) {
            throw ApiException.upstream("Không thể hoàn tất yêu cầu AI. Vui lòng thử lại");
        }
    }

    private URI endpoint(URI base, String path) {
        String value = base.toString().replaceAll("/+$", "");
        return URI.create(value + "/" + path);
    }

}
