package com.example.backend.ai.simulation;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.ai.ocr.OcrProvider;
import com.example.backend.entity.enums.OcrStatus;
import com.example.backend.exception.ApiException;
import com.example.backend.ai.simulation.SimulationFlowResponse.Parameter;
import com.example.backend.ai.simulation.SimulationFlowResponse.Validation;
import com.example.backend.service.account.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Recognition, semantic understanding, and visual-program generation for one AI flow. */
@Service
public class SimulationFlowService {
    private static final Logger log = LoggerFactory.getLogger(SimulationFlowService.class);
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);
    private static final int MAX_INPUT_CHARS = 12_000;
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_SESSIONS = 2_000;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final String RUNTIME_KIND = "runtimeKind";
    private static final String DURATION_SECONDS = "durationSeconds";
    private static final String STATUS = "status";
    private static final String REASON = "reason";
    private static final String VISUAL = "VISUAL";
    private static final String UNSUPPORTED = "UNSUPPORTED";

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final CurrentUserService currentUser;
    private final OcrProvider ocr;
    private final SimulationUnderstandingGateway understanding;
    private final SimulationProgramGateway program;
    private final ObjectMapper mapper;

    public SimulationFlowService(CurrentUserService currentUser, OcrProvider ocr,
            SimulationUnderstandingGateway understanding, SimulationProgramGateway program,
            ObjectMapper mapper) {
        this.currentUser = currentUser;
        this.ocr = ocr;
        this.understanding = understanding;
        this.program = program;
        this.mapper = mapper;
    }

    public SimulationFlowResponse normalize(String sourceMode, String text) {
        if (!"TEXT".equals(sourceMode) && !"LATEX".equals(sourceMode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "sourceMode must be TEXT or LATEX");
        }
        String recognized = validateText(text);
        Session session = addSession(new Session(ownerId(), sourceMode, recognized, 1.0, null));
        return recognitionResponse(session);
    }

    public SimulationFlowResponse normalizeImage(MultipartFile file, String suppliedText) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES
                || !IMAGE_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A PNG, JPEG, or WebP image under 8 MB is required");
        }
        int owner = ownerId();
        try {
            var result = ocr.recognize(file.getContentType(), file.getBytes());
            String recognized = result.status() == OcrStatus.SUCCEEDED ? result.text() : null;
            if (StringUtils.hasText(recognized) && StringUtils.hasText(suppliedText)) {
                recognized = suppliedText.trim() + "\n" + recognized.trim();
            }
            boolean success = StringUtils.hasText(recognized) && recognized.length() <= MAX_INPUT_CHARS;
            String message;
            if (success) {
                message = "OCR confidence is unavailable. Check the recognized text before continuing.";
            } else if (StringUtils.hasText(result.errorMessage())) {
                message = result.errorMessage();
            } else {
                message = "The image could not be recognized reliably. Enter or upload a clearer description.";
            }
            Session session = addSession(new Session(owner, "IMAGE", success ? recognized.trim() : null,
                    null, message));
            if (!success) session.stage = Stage.RECOGNITION_FAILED;
            return recognitionResponse(session);
        } catch (IOException failure) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded image");
        }
    }

    public SimulationFlowResponse confirmInput(UUID id, boolean confirmed, String correctedText) {
        Session session = requireSession(id);
        synchronized (session) {
            if (session.stage != Stage.RECOGNITION && session.stage != Stage.RECOGNITION_FAILED) {
                throw new ApiException(HttpStatus.CONFLICT, "Input recognition has already been handled");
            }
            if (!confirmed) {
                session.recognizedText = validateText(correctedText);
                session.stage = Stage.RECOGNITION;
                session.confidence = 1.0;
                session.message = "Check this corrected description before analysis.";
                return recognitionResponse(session);
            }
            if (session.stage == Stage.RECOGNITION_FAILED) {
                throw new ApiException(HttpStatus.CONFLICT, "Recognition failed; provide corrected text first");
            }
            session.description = session.recognizedText;
            session.stage = Stage.ANALYZING;
            try {
                return analyze(session);
            } catch (RuntimeException failure) {
                session.stage = Stage.RECOGNITION;
                throw failure;
            }
        }
    }

    public SimulationFlowResponse revise(UUID id, String text) {
        Session session = requireSession(id);
        synchronized (session) {
            if (session.stage != Stage.CLARIFY && session.stage != Stage.EXPLAIN
                    && session.stage != Stage.UNSUPPORTED) {
                throw new ApiException(HttpStatus.CONFLICT, "There is no explanation or question to revise");
            }
            String revision = validateText(text);
            Stage previousStage = session.stage;
            String previousSchemaId = session.schemaId;
            String previousQuestion = session.question;
            String previousExplanation = session.explanation;
            List<Parameter> previousParameters = session.parameters;
            List<String> previousDefaults = session.defaults;
            JsonNode previousSpecification = session.specification;
            session.conversation.add(revision);
            session.stage = Stage.ANALYZING;
            try {
                return analyze(session);
            } catch (RuntimeException failure) {
                session.conversation.remove(session.conversation.size() - 1);
                session.stage = previousStage;
                session.schemaId = previousSchemaId;
                session.question = previousQuestion;
                session.explanation = previousExplanation;
                session.parameters = previousParameters;
                session.defaults = previousDefaults;
                session.specification = previousSpecification;
                throw failure;
            }
        }
    }

    public SimulationFlowResponse confirmExplanation(UUID id, boolean confirmed, String text) {
        Session session = requireSession(id);
        synchronized (session) {
            if (!confirmed) return revise(id, text);
            if (session.stage != Stage.EXPLAIN || session.specification == null) {
                throw new ApiException(HttpStatus.CONFLICT, "Confirm the explanation before generating the simulation");
            }
            return generateVisual(session);
        }
    }

    private SimulationFlowResponse generateVisual(Session session) {
        if (!program.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI simulation provider is not configured. Set AI_API_KEY and restart the backend.",
                    "AI_PROVIDER_UNAVAILABLE", "GENERATION");
        }
        JsonNode plan;
        try {
            plan = program.plan(combinedDescription(session), session.explanation,
                    session.specification);
        } catch (RuntimeException failure) {
            log.warn("Visual simulation generation failed: sessionId={} runtime={}",
                    session.id, session.schemaId, failure);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Could not produce a safe visual simulation from the confirmed description.",
                    "VISUAL_PLAN_FAILED", "GENERATION");
        }
        if (plan == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The visual planner returned no plan",
                    "VISUAL_PLAN_FAILED", "GENERATION");
        }
        if (UNSUPPORTED.equals(plan.path(STATUS).asText())) {
            session.stage = Stage.UNSUPPORTED;
            session.explanation = nonblank(plan.path(REASON).asText(null),
                    "The requested phenomenon could not be represented safely.");
            return analysisResponse(session);
        }
        if (!"READY".equals(plan.path(STATUS).asText()) || !plan.path("program").isObject()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The visual plan is incomplete",
                    "VISUAL_PLAN_FAILED", "GENERATION");
        }
        JsonNode duration = plan.path(DURATION_SECONDS);
        if (!duration.isNumber() || !Double.isFinite(duration.doubleValue())
                || duration.doubleValue() <= 0 || duration.doubleValue() > 40) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The visual plan has an invalid duration",
                    "VISUAL_PLAN_FAILED", "GENERATION");
        }
        List<Parameter> parameters = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        JsonNode proposed = plan.path("parameters");
        if (!proposed.isArray() || proposed.size() > 30) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The visual plan has invalid controls",
                    "VISUAL_PLAN_FAILED", "GENERATION");
        }
        for (JsonNode item : proposed) {
            String name = item.path("name").asText("");
            String unit = item.path("unit").asText("");
            double value = item.path("value").asDouble(Double.NaN);
            double min = item.path("min").asDouble(Double.NaN);
            double max = item.path("max").asDouble(Double.NaN);
            if (!name.matches("[A-Za-z_]\\w{0,50}") || !names.add(name)
                    || !Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max)
                    || min > value || value > max || min < -1e30 || max > 1e30) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "The visual plan has invalid controls",
                        "VISUAL_PLAN_FAILED", "GENERATION");
            }
            parameters.add(new Parameter(name, value, unit, min, max,
                    nonblank(item.path("label").asText(null), name)));
        }
        var mutable = (com.fasterxml.jackson.databind.node.ObjectNode) session.specification;
        mutable.put(RUNTIME_KIND, VISUAL);
        mutable.set("visualProgram", plan.path("program").deepCopy());
        mutable.put(DURATION_SECONDS, duration.doubleValue());
        mutable.set("sceneWarnings", mapper.valueToTree(List.of(
                "This AI-generated visual model has not been independently checked against a physics solver. Review its values and behavior.")));
        session.parameters = List.copyOf(parameters);
        session.code = null;
        session.validation = new Validation("UNVERIFIED", List.of());
        session.stage = Stage.SIMULATION;
        return simulationResponse(session);
    }

    public Validation validation(UUID id) {
        Session session = requireSession(id);
        synchronized (session) {
            if (session.stage != Stage.SIMULATION) {
                throw new ApiException(HttpStatus.CONFLICT, "Simulation has not been generated");
            }
            return session.validation;
        }
    }

    public Validation recordValidation(UUID id, String status, List<String> flags,
            Map<String, Double> metrics, Map<String, Double> parameterValues) {
        Session session = requireSession(id);
        synchronized (session) {
            if (session.stage != Stage.SIMULATION) {
                throw new ApiException(HttpStatus.CONFLICT, "Simulation has not been generated");
            }
            boolean visual = VISUAL.equals(session.specification.path(RUNTIME_KIND).asText());
            if (!"FLAGGED".equals(status) && !(visual ? "UNVERIFIED".equals(status) : "OK".equals(status))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation status does not match this runtime");
            }
            List<String> checkedFlags = flags == null ? List.of() : flags.stream()
                    .filter(StringUtils::hasText).map(String::trim).limit(8)
                    .map(flag -> flag.substring(0, Math.min(flag.length(), 300))).toList();
            Map<String, Double> checkedMetrics = new HashMap<>();
            if (metrics != null) {
                metrics.entrySet().stream().limit(20).forEach(entry -> {
                    if (entry.getKey() != null && entry.getKey().matches("[A-Za-z]\\w{0,50}")
                            && entry.getValue() != null && Double.isFinite(entry.getValue())) {
                        checkedMetrics.put(entry.getKey(), entry.getValue());
                    }
                });
            }
            Map<String, Parameter> declaredParameters = new HashMap<>();
            session.parameters.forEach(parameter -> declaredParameters.put(parameter.name(), parameter));
            Map<String, Double> checkedParameters = new HashMap<>();
            if (parameterValues == null) {
                session.parameters.forEach(parameter -> checkedParameters.put(parameter.name(), parameter.value()));
            } else {
                if (parameterValues.size() > 40 || !parameterValues.keySet().equals(declaredParameters.keySet())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Validation parameters do not match the generated scene");
                }
                parameterValues.forEach((name, value) -> {
                    Parameter declared = declaredParameters.get(name);
                    if (value == null || !Double.isFinite(value)
                            || value < declared.min() || value > declared.max()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST,
                                "Validation parameter is outside the generated range");
                    }
                    checkedParameters.put(name, value);
                });
            }
            session.validation = new Validation(status, checkedFlags);
            if ("FLAGGED".equals(status) && log.isWarnEnabled()) {
                    String description = combinedDescription(session);
                    Object code = visual ? session.specification.path("visualProgram") : session.code;
                    log.warn("Simulation validation flag for human review: sessionId={} schemaId={} sourceMode={} "
                                    + "description={} code={} parameters={} flags={} metrics={}",
                            id, session.schemaId, session.sourceMode, description, code,
                            checkedParameters, checkedFlags, checkedMetrics);
            }
            return session.validation;
        }
    }

    private SimulationFlowResponse analyze(Session session) {
        if (!understanding.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI simulation provider is not configured. Set AI_API_KEY and restart the backend.",
                    "AI_PROVIDER_UNAVAILABLE", "ANALYSIS");
        }
        JsonNode conversation = mapper.valueToTree(session.conversation);
        JsonNode answer;
        try {
            answer = understanding.understand(session.description, conversation);
        } catch (ApiException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            log.error("Simulation understanding failed: sessionId={} schemaId={}",
                    session.id, session.schemaId, failure);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The AI provider could not understand the simulation description. Check the AI provider configuration and try again.",
                    "AI_UNDERSTANDING_FAILED", "ANALYSIS");
        }
        String status = answer.path(STATUS).asText("");
        if (UNSUPPORTED.equals(status)) {
            session.stage = Stage.UNSUPPORTED;
            session.explanation = nonblank(answer.path("explanation").asText(null),
                    nonblank(answer.path(REASON).asText(null),
                            "The confirmed phenomenon cannot be modeled honestly by the available runtime."));
            return analysisResponse(session);
        }
        if ("CLARIFY".equals(status)) {
            String question = answer.path("question").asText("").trim();
            if (question.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "The analysis requested clarification without a question",
                        "INVALID_ANALYSIS", "ANALYSIS");
            }
            if (question.length() > 2000) question = question.substring(0, 2000);
            session.question = question;
            session.stage = Stage.CLARIFY;
            return analysisResponse(session);
        }
        if (!"EXPLAIN".equals(status)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The analysis returned an invalid workflow status",
                    "INVALID_ANALYSIS", "ANALYSIS");
        }
        String explanation = answer.path("explanation").asText("").trim();
        if (explanation.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The analysis omitted the simulation explanation",
                    "INVALID_ANALYSIS", "ANALYSIS");
        }
        if (explanation.length() > 20000) {
            explanation = explanation.substring(0, 20000);
        }
        JsonNode spec = answer.path("simulationSpec");
        if (!spec.isObject()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The analysis omitted the simulation specification",
                    "INVALID_ANALYSIS", "ANALYSIS");
        }
        var copy = (com.fasterxml.jackson.databind.node.ObjectNode) spec.deepCopy();
        copy.put(RUNTIME_KIND, VISUAL);
        session.parameters = List.of();
        session.defaults = readDefaults(answer.path("defaults"));
        session.specification = copy;
        session.explanation = explanation;
        session.question = null;
        session.stage = Stage.EXPLAIN;
        return analysisResponse(session);
    }

    private List<String> readDefaults(JsonNode node) {
        if (!node.isArray()) {
            log.warn("AI returned non-array defaults, using empty list");
            return List.of();
        }
        List<String> defaults = new ArrayList<>();
        int characters = 0;
        for (JsonNode item : node) {
            String text = item.isTextual() ? item.asText() : item.toString();
            characters += text.length();
            if (characters > MAX_INPUT_CHARS * 4) break;
            defaults.add(text);
        }
        return List.copyOf(defaults);
    }

    private SimulationFlowResponse recognitionResponse(Session session) {
        return new SimulationFlowResponse(session.id, session.stage.name(), session.recognizedText,
                session.recognizedText, session.sourceMode, session.confidence, session.message,
                null, null, null, null, null, null, null, null);
    }

    private SimulationFlowResponse analysisResponse(Session session) {
        return new SimulationFlowResponse(session.id, session.stage.name(), null, null,
                null, null, session.message, session.question, session.explanation,
                session.stage == Stage.EXPLAIN ? session.parameters : null,
                session.stage == Stage.EXPLAIN ? session.defaults : null,
                session.schemaId, null,
                session.stage == Stage.EXPLAIN ? session.specification : null, null);
    }

    private SimulationFlowResponse simulationResponse(Session session) {
        return new SimulationFlowResponse(session.id, session.stage.name(), null, null,
                null, null, null, null, session.explanation, session.parameters,
                session.defaults, session.schemaId, session.code, session.specification,
                session.validation);
    }

    private Session addSession(Session session) {
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(Instant.now()));
        if (sessions.size() >= MAX_SESSIONS) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Too many active simulations; try again shortly");
        }
        sessions.put(session.id, session);
        return session;
    }

    private Session requireSession(UUID id) {
        Session session = sessions.get(id);
        if (session == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Simulation flow session not found or expired");
        }
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(id, session);
            throw new ApiException(HttpStatus.NOT_FOUND, "Simulation flow session not found or expired");
        }
        if (session.ownerId != ownerId()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Simulation flow session not found or expired");
        }
        session.expiresAt = Instant.now().plus(SESSION_LIFETIME);
        return session;
    }

    private int ownerId() { return currentUser.requireCurrentUser().getId(); }

    private String validateText(String text) {
        if (!StringUtils.hasText(text) || text.length() > MAX_INPUT_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Description must contain 1 to 12000 characters");
        }
        return text.trim();
    }

    private String combinedDescription(Session session) {
        return session.description + (session.conversation.isEmpty() ? ""
                : "\n" + String.join("\n", session.conversation));
    }

    private String nonblank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private enum Stage { RECOGNITION, RECOGNITION_FAILED, ANALYZING, CLARIFY, EXPLAIN,
        UNSUPPORTED, SIMULATION }

    private static final class Session {
        private final UUID id = UUID.randomUUID();
        private final int ownerId;
        private final String sourceMode;
        private final List<String> conversation = new ArrayList<>();
        private volatile Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
        private Stage stage = Stage.RECOGNITION;
        private String recognizedText;
        private Double confidence;
        private String message;
        private String description;
        private String schemaId;
        private String question;
        private String explanation;
        private List<Parameter> parameters = List.of();
        private List<String> defaults = List.of();
        private JsonNode specification;
        private String code;
        private Validation validation;

        private Session(int ownerId, String sourceMode, String recognizedText,
                Double confidence, String message) {
            this.ownerId = ownerId;
            this.sourceMode = sourceMode;
            this.recognizedText = recognizedText;
            this.confidence = confidence;
            this.message = message;
        }
    }
}
