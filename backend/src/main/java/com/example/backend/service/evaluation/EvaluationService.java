package com.example.backend.service.evaluation;

import com.example.backend.repository.evaluation.BenchmarkProblemRepository;
import com.example.backend.repository.evaluation.EvaluationRunRepository;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.account.CurrentUserService;

import com.example.backend.dto.evaluation.EvaluationResponse;
import com.example.backend.entity.evaluation.Adjudication;
import com.example.backend.entity.evaluation.BenchmarkProblem;
import com.example.backend.entity.evaluation.EvaluationRun;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.ai.extraction.ExtractionCoordinator;
import com.example.backend.ai.extraction.model.ExtractionResult;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EvaluationService {
    private static final String QUANTITIES_FIELD = "quantities";
    private static final String NORMALIZED_VALUE_FIELD = "normalizedValue";
    private static final String NORMALIZED_UNIT_FIELD = "normalizedUnit";
    private static final String UNKNOWN_VALUE = "unknown";

    private final com.example.backend.repository.evaluation.BenchmarkProblemRepository benchmarkRepository;
    private final com.example.backend.repository.evaluation.EvaluationRunRepository evaluationRepository;
    private final ExtractionCoordinator extractionCoordinator;
    private final ObjectMapper objectMapper;
    private final SchemaDefinitionService schemaDefinitions;
    private final CurrentUserService currentUser;
    private final PlatformTransactionManager transactionManager;

    public record RunView(UUID id, String evaluationType, String status, int benchmarkCount,
                          JsonNode metrics, String report, String requestedByReference,
                          Instant createdAt, Instant startedAt, Instant completedAt,
                          Long durationMs, String benchmarkSnapshotHash, JsonNode configuration,
                          String failureCode, String failureMessage) {
        static RunView from(EvaluationRun run) {
            return new RunView(run.getId(), run.getEvaluationType(), run.getStatus(), run.getBenchmarkCount(),
                    run.getMetrics(), run.getReport(), run.getRequestedByReference(), run.getCreatedAt(),
                    run.getStartedAt(), run.getCompletedAt(), run.getDurationMs(), run.getBenchmarkSnapshotHash(),
                    run.getConfiguration(), run.getFailureCode(), run.getFailureMessage());
        }
    }

    public record RunPage(List<RunView> items, int page, int size, long totalElements, int totalPages) { }

    public record RunComparison(RunView baseline, RunView candidate, JsonNode metricDelta) { }

    private record EvaluationCase(UUID id, String topic, String problemText, Instant updatedAt,
                                  JsonNode goldSpecification, JsonNode firstAnnotation, JsonNode secondAnnotation) { }

    private record PreparedRun(UUID id, String actor, Instant startedAt, String snapshotHash,
                               List<EvaluationCase> cases) { }

    public EvaluationResponse run() {
        PreparedRun prepared = prepareRun();
        List<CaseMetrics> confirmFlow = new ArrayList<>();
        List<CaseMetrics> silentDefault = new ArrayList<>();
        Map<String, String> schemaVersions = new LinkedHashMap<>();
        Map<String, JsonNode> schemaDefinitionsUsed = new LinkedHashMap<>();
        Map<String, String> modelVersions = new LinkedHashMap<>();
        try {
            for (EvaluationCase benchmark : prepared.cases()) {
                ExtractionResult extraction = extractionCoordinator.extract(benchmark.problemText());
                JsonNode predicted = objectMapper.valueToTree(extraction.document());
                SchemaDefinitionService.DefinitionSnapshot schema = schemaDefinitions
                        .approvedDefinitionSnapshot(extraction.document().schemaId());
                JsonNode definition = schema.definition();
                double tolerance = definition.path("validation").path("tolerance").asDouble(0.0);
                confirmFlow.add(compare(benchmark, predicted, benchmark.goldSpecification(), definition, tolerance,
                        extraction.modelVersion()));
                silentDefault.add(compare(benchmark, addSilentDefaults(predicted, definition),
                        benchmark.goldSpecification(), definition, tolerance, extraction.modelVersion()));
                schemaVersions.put(schema.schemaId(), schema.version());
                schemaDefinitionsUsed.put(schema.schemaId(), schema.definition());
                modelVersions.put(benchmark.id().toString(), extraction.modelVersion());
            }

            Summary total = summarize(confirmFlow);
            ObjectNode metrics = objectMapper.createObjectNode();
            metrics.set("confirmFlow", report(confirmFlow, "CONFIRM_FLOW"));
            metrics.set("silentDefaultBaseline", report(silentDefault, "SILENT_DEFAULT_BASELINE"));
            metrics.set("byTopic", groupedReport(confirmFlow, CaseMetrics::topic));
            metrics.set("byQuantityType", groupedQuantityReport(confirmFlow));
            metrics.put("numericAgreement", total.numericAgreement());
            metrics.put("incorrectSimulationRate", total.incorrectRate());
            metrics.put("extractionModel", providerVersion(confirmFlow));
            metrics.put("evaluationDesign", "Fixed benchmark corpus; same extraction and schema versions in both conditions.");

            ObjectNode configuration = configurationSnapshot(prepared.cases());
            ObjectNode bindings = objectMapper.createObjectNode();
            schemaVersions.forEach(bindings::put);
            configuration.set("schemaVersionsUsed", bindings);
            ObjectNode definitions = objectMapper.createObjectNode();
            schemaDefinitionsUsed.forEach(definitions::set);
            configuration.set("schemaDefinitionsUsed", definitions);
            ObjectNode models = objectMapper.createObjectNode();
            modelVersions.forEach(models::put);
            configuration.set("modelVersionsUsed", models);
            completeRun(prepared, metrics, configuration, total, cohenKappa(prepared.cases()));
            return new EvaluationResponse("CONFIRM_FLOW_VS_SILENT_DEFAULT", prepared.cases().size(), total.precision(),
                    total.recall(), total.f1(), cohenKappa(prepared.cases()), total.incorrectRate(), metrics);
        } catch (ApiException exception) {
            markRunFailed(prepared, exception.getClass().getSimpleName(), safeFailureMessage(exception));
            throw exception;
        } catch (RuntimeException exception) {
            markRunFailed(prepared, "EVALUATION_FAILED", safeFailureMessage(exception));
            throw new com.example.backend.exception.ApiException(HttpStatus.BAD_GATEWAY,
                    "Evaluation failed. Cause: " + safeFailureMessage(exception));
        }
    }

    private PreparedRun prepareRun() {
        return transactionTemplate().execute(status -> {
            Instant startedAt = Instant.now();
            String actor = "user:" + currentUser.requireCurrentUser().getId();
            List<BenchmarkProblem> benchmarks = finalizedBenchmarks();
            if (benchmarks.isEmpty()) {
                throw new com.example.backend.exception.ApiException(HttpStatus.CONFLICT,
                        "No finalized gold benchmarks are available");
            }
            List<EvaluationCase> cases = benchmarks.stream().map(this::snapshotCase).toList();
            String snapshotHash = snapshotHash(cases);
            EvaluationRun run = new EvaluationRun();
            run.setEvaluationType("CONFIRM_FLOW_VS_SILENT_DEFAULT");
            run.setBenchmarkCount(cases.size());
            run.setMetrics(objectMapper.createObjectNode());
            run.setStatus("RUNNING");
            run.setRequestedByReference(actor);
            run.setStartedAt(startedAt);
            run.setBenchmarkSnapshotHash(snapshotHash);
            run.setConfiguration(configurationSnapshot(cases));
            evaluationRepository.saveAndFlush(run);
            return new PreparedRun(run.getId(), actor, startedAt, snapshotHash, cases);
        });
    }

    private EvaluationCase snapshotCase(BenchmarkProblem benchmark) {
        JsonNode first = benchmark.getAnnotations().size() > 0
                ? copy(benchmark.getAnnotations().get(0).getGoldSpecification()) : null;
        JsonNode second = benchmark.getAnnotations().size() > 1
                ? copy(benchmark.getAnnotations().get(1).getGoldSpecification()) : null;
        return new EvaluationCase(benchmark.getId(), benchmark.getTopic(), benchmark.getProblemText(),
                benchmark.getUpdatedAt(), copy(goldSpecification(benchmark)), first, second);
    }

    private ObjectNode configurationSnapshot(List<EvaluationCase> cases) {
        ObjectNode configuration = objectMapper.createObjectNode();
        configuration.put("evaluationType", "CONFIRM_FLOW_VS_SILENT_DEFAULT");
        configuration.put("schemaCatalogMode", "approved");
        configuration.put("toleranceSource", "approved schema validation.tolerance");
        ArrayNode snapshot = configuration.putArray("benchmarkCorpusSnapshot");
        cases.forEach(item -> {
            ObjectNode entry = snapshot.addObject();
            entry.put("id", item.id().toString());
            entry.put("topic", item.topic());
            entry.put("updatedAt", item.updatedAt().toString());
            entry.put("problemText", item.problemText());
            entry.set("goldSpecification", item.goldSpecification() == null
                    ? objectMapper.nullNode() : item.goldSpecification());
        });
        return configuration;
    }

    private void completeRun(PreparedRun prepared, JsonNode metrics, JsonNode configuration,
                             Summary total, double kappa) {
        transactionTemplate().executeWithoutResult(status -> {
            EvaluationRun run = evaluationRepository.findById(prepared.id())
                    .orElseThrow(() -> new com.example.backend.exception.ApiException(HttpStatus.NOT_FOUND,
                            "Evaluation run not found"));
            if (!"RUNNING".equals(run.getStatus())) {
                throw new com.example.backend.exception.ApiException(HttpStatus.CONFLICT,
                        "Evaluation run is no longer active");
            }
            Instant completedAt = Instant.now();
            run.setMetrics(metrics);
            run.setConfiguration(configuration);
            run.setStatus("COMPLETED");
            run.setCompletedAt(completedAt);
            run.setDurationMs(java.time.Duration.between(prepared.startedAt(), completedAt).toMillis());
            run.setReport("Confirm-flow metrics are compared with a deterministic silent-default baseline. "
                    + "The baseline is evaluation-only and never participates in production simulation creation. "
                    + "Agreement=" + kappa + ", F1=" + total.f1());
            evaluationRepository.save(run);
        });
    }

    private void markRunFailed(PreparedRun prepared, String code, String message) {
        try {
            transactionTemplate().executeWithoutResult(status -> evaluationRepository.findById(prepared.id()).ifPresent(run -> {
                if (!"RUNNING".equals(run.getStatus())) return;
                Instant completedAt = Instant.now();
                run.setStatus("FAILED");
                run.setFailureCode(code);
                run.setFailureMessage(message);
                run.setCompletedAt(completedAt);
                run.setDurationMs(java.time.Duration.between(prepared.startedAt(), completedAt).toMillis());
                evaluationRepository.save(run);
            }));
        } catch (RuntimeException ignored) {
            // Preserve the original evaluation error if failure bookkeeping cannot commit.
        }
    }

    private String safeFailureMessage(Throwable failure) {
        String message = failure.getMessage();
        if (!StringUtils.hasText(message)) return failure.getClass().getSimpleName();
        String sanitized = message.replaceAll("(?i)bearer\\s+[^\\s,]+", "Bearer [redacted]");
        return sanitized.substring(0, Math.min(240, sanitized.length()));
    }

    private JsonNode copy(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public RunPage history(String status, Pageable pageable) {
        Page<EvaluationRun> page = evaluationRepository.search(StringUtils.hasText(status) ? status.trim() : null, pageable);
        return new RunPage(page.getContent().stream().map(RunView::from).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RunView detail(UUID id) {
        return RunView.from(evaluationRepository.findById(id)
                .orElseThrow(() -> new com.example.backend.exception.ApiException(HttpStatus.NOT_FOUND,
                        "Evaluation run not found")));
    }

    @Transactional(readOnly = true)
    public RunComparison compare(UUID baselineId, UUID candidateId) {
        RunView baseline = detail(baselineId);
        RunView candidate = detail(candidateId);
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("precision", numericDelta(baseline.metrics(), candidate.metrics(), "confirmFlow", "precision"));
        delta.put("recall", numericDelta(baseline.metrics(), candidate.metrics(), "confirmFlow", "recall"));
        delta.put("f1", numericDelta(baseline.metrics(), candidate.metrics(), "confirmFlow", "f1"));
        delta.put("numericAgreement", numericDelta(baseline.metrics(), candidate.metrics(), null, "numericAgreement"));
        delta.put("incorrectSimulationRate", numericDelta(baseline.metrics(), candidate.metrics(), null, "incorrectSimulationRate"));
        return new RunComparison(baseline, candidate, delta);
    }

    private double numericDelta(JsonNode baseline, JsonNode candidate, String parent, String field) {
        JsonNode left = parent == null ? baseline.path(field) : baseline.path(parent).path(field);
        JsonNode right = parent == null ? candidate.path(field) : candidate.path(parent).path(field);
        return right.asDouble(0.0) - left.asDouble(0.0);
    }

    private String snapshotHash(List<EvaluationCase> benchmarks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            benchmarks.stream()
                    .map(item -> item.id() + ":" + item.updatedAt())
                    .sorted()
                    .forEach(value -> digest.update(value.getBytes(StandardCharsets.UTF_8)));
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private List<BenchmarkProblem> finalizedBenchmarks() {
        return benchmarkRepository.findFinalizedActive();
    }

    private JsonNode goldSpecification(BenchmarkProblem benchmark) {
        return benchmark.getAdjudications().stream().findFirst()
                .map(Adjudication::getResolvedSpecification)
                .orElseGet(() -> benchmark.getAnnotations().stream().findFirst()
                        .map(annotation -> annotation.getGoldSpecification()).orElse(null));
    }

    private JsonNode schemaDefinition(String schemaId) {
        SchemaVersion schema = schemaDefinitions.requireApproved(schemaId);
        return schema.getDefinition();
    }

    private JsonNode addSilentDefaults(JsonNode predicted, JsonNode definition) {
        ObjectNode baseline = predicted != null && predicted.isObject()
                ? (ObjectNode) predicted.deepCopy() : objectMapper.createObjectNode();
        ArrayNode quantities = baseline.withArray(QUANTITIES_FIELD);
        Set<String> present = new HashSet<>();
        quantities.forEach(quantity -> present.add(quantity.path("name").asText().toLowerCase(Locale.ROOT)));
        for (JsonNode required : definition.path("requiredQuantities")) {
            String key = required.path("key").asText();
            if (key.isBlank() || present.contains(key.toLowerCase(Locale.ROOT))) continue;
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("name", key);
            fallback.put("symbol", required.path("aliases").path(0).asText(key));
            fallback.put("value", 0.0);
            fallback.put("originalUnit", firstUnit(required));
            fallback.put(NORMALIZED_VALUE_FIELD, 0.0);
            fallback.put(NORMALIZED_UNIT_FIELD, firstUnit(required));
            fallback.put("confidence", 0.0);
            fallback.put("sourceText", "silent-default baseline");
            quantities.add(fallback);
            present.add(key.toLowerCase(Locale.ROOT));
        }
        return baseline;
    }

    private String firstUnit(JsonNode required) {
        return required.path("allowedUnits").path(0).asText("SI");
    }

    private CaseMetrics compare(BenchmarkProblem benchmark, JsonNode predicted, JsonNode gold,
                                JsonNode definition, double tolerance, String provider) {
        return compareValues(benchmark.getId(), benchmark.getTopic(), predicted, gold, definition, tolerance, provider);
    }

    private CaseMetrics compare(EvaluationCase benchmark, JsonNode predicted, JsonNode gold,
                                JsonNode definition, double tolerance, String provider) {
        return compareValues(benchmark.id(), benchmark.topic(), predicted, gold, definition, tolerance, provider);
    }

    private CaseMetrics compareValues(UUID benchmarkId, String topic, JsonNode predicted, JsonNode gold,
                                      JsonNode definition, double tolerance, String provider) {
        Map<String, QuantityValue> actual = quantities(predicted, definition);
        Map<String, QuantityValue> expected = quantities(gold, definition);
        int truePositive = 0;
        int numericMatches = 0;
        int numericComparable = 0;
        for (Map.Entry<String, QuantityValue> entry : actual.entrySet()) {
            QuantityValue expectedValue = expected.get(entry.getKey());
            if (expectedValue == null) continue;
            truePositive++;
            if (numericMatch(entry.getValue(), expectedValue, tolerance)) numericMatches++;
            if (entry.getValue().number() && expectedValue.number()) numericComparable++;
        }
        boolean exact = actual.keySet().equals(expected.keySet())
                && numericMatches == numericComparable && numericComparable == expected.size();
        Map<String, String> types = quantityTypes(actual, expected);
        Map<String, Boolean> numericMatchByKey = new LinkedHashMap<>();
        Map<String, Boolean> numericComparableByKey = new LinkedHashMap<>();
        for (String key : types.keySet()) {
            QuantityValue actualValue = actual.get(key);
            QuantityValue expectedValue = expected.get(key);
            boolean comparable = actualValue != null && expectedValue != null
                    && actualValue.number() && expectedValue.number();
            numericComparableByKey.put(key, comparable);
            numericMatchByKey.put(key, comparable && numericMatch(actualValue, expectedValue, tolerance));
        }
        return new CaseMetrics(benchmarkId == null ? UNKNOWN_VALUE : benchmarkId.toString(),
                topic, provider == null ? UNKNOWN_VALUE : provider, truePositive, actual.size(),
                expected.size(), numericMatches, numericComparable, exact, types, actual, expected,
                numericMatchByKey, numericComparableByKey);
    }

    private Map<String, QuantityValue> quantities(JsonNode document, JsonNode definition) {
        Map<String, QuantityValue> result = new LinkedHashMap<>();
        if (document == null || !document.path(QUANTITIES_FIELD).isArray()) return result;
        document.path(QUANTITIES_FIELD).forEach(quantity -> {
            String name = canonicalName(quantity.path("name").asText(), definition);
            if (!name.isBlank()) result.put(name, QuantityValue.from(quantity, quantityType(name)));
        });
        return result;
    }

    private String canonicalName(String raw, JsonNode definition) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (JsonNode required : definition.path("requiredQuantities")) {
            if (required.path("key").asText().equalsIgnoreCase(normalized)) return required.path("key").asText();
            for (JsonNode alias : required.path("aliases")) {
                if (alias.asText().equalsIgnoreCase(normalized)) return required.path("key").asText();
            }
        }
        return raw == null ? "" : raw.trim();
    }

    private Map<String, String> quantityTypes(Map<String, QuantityValue> actual,
                                               Map<String, QuantityValue> expected) {
        Map<String, String> result = new LinkedHashMap<>();
        actual.forEach((key, value) -> result.put(key, value.type()));
        expected.forEach((key, value) -> result.putIfAbsent(key, value.type()));
        return result;
    }

    private boolean numericMatch(QuantityValue actual, QuantityValue expected, double tolerance) {
        return actual.number() && expected.number() && actual.unit().equalsIgnoreCase(expected.unit())
                && Math.abs(actual.value() - expected.value()) <= tolerance;
    }

    private ObjectNode report(List<CaseMetrics> cases, String condition) {
        Summary summary = summarize(cases);
        ObjectNode result = summaryJson(summary);
        result.put("condition", condition);
        result.set("details", objectMapper.valueToTree(cases));
        return result;
    }

    private ObjectNode groupedReport(List<CaseMetrics> cases, Function<CaseMetrics, String> key) {
        Map<String, List<CaseMetrics>> groups = new LinkedHashMap<>();
        cases.forEach(item -> groups.computeIfAbsent(key.apply(item), ignored -> new ArrayList<>()).add(item));
        ObjectNode result = objectMapper.createObjectNode();
        groups.forEach((name, values) -> result.set(name, summaryJson(summarize(values))));
        return result;
    }

    private ObjectNode groupedQuantityReport(List<CaseMetrics> cases) {
        Map<String, QuantitySummary> groups = new LinkedHashMap<>();
        for (CaseMetrics item : cases) {
            for (String key : item.quantityTypes().keySet()) {
                String type = item.quantityTypes().get(key);
                groups.computeIfAbsent(type, ignored -> new QuantitySummary()).add(item, key);
            }
        }
        ObjectNode result = objectMapper.createObjectNode();
        groups.forEach((name, values) -> result.set(name, summaryJson(values.toSummary())));
        return result;
    }

    private ObjectNode summaryJson(Summary summary) {
        return objectMapper.createObjectNode()
                .put("benchmarkCount", summary.benchmarkCount())
                .put("precision", summary.precision())
                .put("recall", summary.recall())
                .put("f1", summary.f1())
                .put("numericAgreement", summary.numericAgreement())
                .put("incorrectRate", summary.incorrectRate());
    }

    private Summary summarize(List<CaseMetrics> cases) {
        int truePositive = cases.stream().mapToInt(CaseMetrics::truePositive).sum();
        int predicted = cases.stream().mapToInt(CaseMetrics::predicted).sum();
        int expected = cases.stream().mapToInt(CaseMetrics::expected).sum();
        int numericMatches = cases.stream().mapToInt(CaseMetrics::numericMatches).sum();
        int numericComparable = cases.stream().mapToInt(CaseMetrics::numericComparable).sum();
        long incorrect = cases.stream().filter(item -> !item.exact()).count();
        double precision = predicted == 0 ? 0 : (double) truePositive / predicted;
        double recall = expected == 0 ? 0 : (double) truePositive / expected;
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new Summary(cases.size(), precision, recall, f1,
                numericComparable == 0 ? 0 : (double) numericMatches / numericComparable,
                cases.isEmpty() ? 0 : (double) incorrect / cases.size());
    }

    private String providerVersion(List<CaseMetrics> cases) {
        return cases.stream().map(CaseMetrics::provider).filter(value -> !value.isBlank()).findFirst()
                .orElse(UNKNOWN_VALUE);
    }

    private String quantityType(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.contains("mass")) return "MASS";
        if (normalized.contains("position") || normalized.equals("x") || normalized.equals("y")) return "POSITION";
        if (normalized.contains("velocity")) return "VELOCITY";
        if (normalized.contains("acceleration")) return "ACCELERATION";
        if (normalized.contains("force")) return "FORCE";
        if (normalized.contains("voltage")) return "VOLTAGE";
        if (normalized.contains("resistance")) return "RESISTANCE";
        if (normalized.contains("capacitance")) return "CAPACITANCE";
        return "OTHER";
    }

    private double cohenKappa(List<EvaluationCase> benchmarks) {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        for (EvaluationCase benchmark : benchmarks) {
            if (benchmark.firstAnnotation() == null || benchmark.secondAnnotation() == null) continue;
            first.add(quantitySignature(benchmark.firstAnnotation()));
            second.add(quantitySignature(benchmark.secondAnnotation()));
        }
        if (first.isEmpty()) return 0;
        int agreement = 0;
        Set<String> labels = new HashSet<>(); labels.addAll(first); labels.addAll(second);
        for (int i = 0; i < first.size(); i++) if (first.get(i).equals(second.get(i))) agreement++;
        double observed = (double) agreement / first.size();
        double expected = 0;
        for (String label : labels) {
            long left = first.stream().filter(label::equals).count();
            long right = second.stream().filter(label::equals).count();
            expected += ((double) left / first.size()) * ((double) right / second.size());
        }
        if (Math.abs(1 - expected) < 1e-9) {
            return observed == 1 ? 1 : 0;
        }
        return (observed - expected) / (1 - expected);
    }

    private String quantitySignature(JsonNode specification) {
        Set<String> keys = new HashSet<>();
        if (specification != null && specification.path(QUANTITIES_FIELD).isArray()) {
            specification.path(QUANTITIES_FIELD).forEach(quantity -> keys.add(quantity.path("name").asText()
                    + "|" + quantity.path(NORMALIZED_VALUE_FIELD).asText() + "|"
                    + quantity.path(NORMALIZED_UNIT_FIELD).asText()));
        }
        return keys.toString();
    }

    private record QuantityValue(String type, double value, String unit, boolean number) {
        static QuantityValue from(JsonNode quantity, String type) {
            JsonNode value = quantity.path(NORMALIZED_VALUE_FIELD);
            return new QuantityValue(type, value.asDouble(), quantity.path(NORMALIZED_UNIT_FIELD).asText(""),
                    value.isNumber() && Double.isFinite(value.asDouble()));
        }
    }

    private record CaseMetrics(String id, String topic, String provider, int truePositive, int predicted,
                               int expected, int numericMatches, int numericComparable, boolean exact,
                               Map<String, String> quantityTypes, Map<String, QuantityValue> actual,
                               Map<String, QuantityValue> expectedValues, Map<String, Boolean> numericMatchByKey,
                               Map<String, Boolean> numericComparableByKey) { }

    private static final class QuantitySummary {
        private final Set<String> cases = new HashSet<>();
        private int truePositive;
        private int predicted;
        private int expected;
        private int numericMatches;
        private int numericComparable;

        private void add(CaseMetrics item, String key) {
            cases.add(item.id());
            if (item.actual().containsKey(key)) predicted++;
            if (item.expectedValues().containsKey(key)) expected++;
            if (item.actual().containsKey(key) && item.expectedValues().containsKey(key)) truePositive++;
            if (Boolean.TRUE.equals(item.numericMatchByKey().get(key))) numericMatches++;
            if (Boolean.TRUE.equals(item.numericComparableByKey().get(key))) numericComparable++;
        }

        private Summary toSummary() {
            double precision = predicted == 0 ? 0 : (double) truePositive / predicted;
            double recall = expected == 0 ? 0 : (double) truePositive / expected;
            double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
            return new Summary(cases.size(), precision, recall, f1,
                    numericComparable == 0 ? 0 : (double) numericMatches / numericComparable, 0);
        }
    }

    private record Summary(int benchmarkCount, double precision, double recall, double f1,
                           double numericAgreement, double incorrectRate) { }
}
