package com.example.backend.service;

import com.example.backend.dto.evaluation.EvaluationResponse;
import com.example.backend.entity.Adjudication;
import com.example.backend.entity.BenchmarkProblem;
import com.example.backend.entity.EvaluationRun;
import com.example.backend.extraction.PhysicalQuantity;
import com.example.backend.extraction.ExtractionCoordinator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvaluationService {
    private final com.example.backend.repository.BenchmarkProblemRepository benchmarkRepository;
    private final com.example.backend.repository.EvaluationRunRepository evaluationRepository;
    private final ExtractionCoordinator extractionCoordinator;
    private final ObjectMapper objectMapper;

    @Transactional
    public EvaluationResponse run() {
        List<BenchmarkProblem> benchmarks = benchmarkRepository.findAll().stream()
                .filter(BenchmarkProblem::isActive)
                .filter(b -> !b.getAdjudications().isEmpty() || (b.getAnnotations().size() == 2
                        && !b.getAnnotations().get(0).getAnnotatorReference().equals(b.getAnnotations().get(1).getAnnotatorReference())
                        && b.getAnnotations().get(0).getGoldSpecification().equals(b.getAnnotations().get(1).getGoldSpecification())))
                .toList();
        if (benchmarks.isEmpty()) throw new com.example.backend.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                "No finalized gold benchmarks are available");
        int truePositive = 0;
        int predicted = 0;
        int expected = 0;
        int incorrect = 0;
        List<JsonNode> details = new ArrayList<>();
        for (BenchmarkProblem benchmark : benchmarks) {
            JsonNode predictedJson = objectMapper.valueToTree(extractionCoordinator.extract(benchmark.getProblemText()).document());
            JsonNode expectedJson = benchmark.getAdjudications().stream().findFirst()
                    .map(Adjudication::getResolvedSpecification)
                    .orElseGet(() -> benchmark.getAnnotations().stream().findFirst().map(a -> a.getGoldSpecification()).orElse(null));
            Set<String> actual = quantityKeys(predictedJson);
            Set<String> gold = quantityKeys(expectedJson);
            Set<String> overlap = new HashSet<>(actual);
            overlap.retainAll(gold);
            truePositive += overlap.size();
            predicted += actual.size();
            expected += gold.size();
            if (!actual.equals(gold)) incorrect++;
            details.add(objectMapper.createObjectNode()
                    .put("id", benchmark.getId().toString())
                    .put("correct", actual.equals(gold))
                    .put("predictedQuantities", actual.size())
                    .put("goldQuantities", gold.size()));
        }
        double precision = predicted == 0 ? 0 : (double) truePositive / predicted;
        double recall = expected == 0 ? 0 : (double) truePositive / expected;
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        double kappa = cohenKappa(benchmarks);
        double incorrectRate = benchmarks.isEmpty() ? 0 : (double) incorrect / benchmarks.size();
        JsonNode metrics = objectMapper.createObjectNode()
                .put("precision", precision).put("recall", recall).put("f1", f1)
                .put("kappa", kappa).put("incorrectRate", incorrectRate)
                .set("details", objectMapper.valueToTree(details));
        EvaluationRun run = new EvaluationRun();
        run.setEvaluationType("UNDERSTANDING_ENGINE");
        run.setBenchmarkCount(benchmarks.size());
        run.setMetrics(metrics);
        run.setReport("Metrics are reproducible from the versioned benchmark corpus.");
        evaluationRepository.save(run);
        return new EvaluationResponse("UNDERSTANDING_ENGINE", benchmarks.size(), precision, recall, f1, kappa, incorrectRate, metrics);
    }

    private Set<String> quantityKeys(JsonNode document) {
        Set<String> keys = new java.util.TreeSet<>();
        if (document == null || !document.has("quantities")) return keys;
        for (JsonNode quantity : document.get("quantities")) {
            String name = quantity.path("name").asText("");
            String unit = quantity.path("normalizedUnit").asText("");
            String value = quantity.path("normalizedValue").asText("");
            keys.add(name + "|" + value + "|" + unit);
        }
        return keys;
    }

    private double cohenKappa(List<BenchmarkProblem> benchmarks) {
        if (benchmarks.isEmpty()) return 0;
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        for (BenchmarkProblem benchmark : benchmarks) {
            if (benchmark.getAnnotations().size() < 2) continue;
            first.add(quantityKeys(benchmark.getAnnotations().get(0).getGoldSpecification()).toString());
            second.add(quantityKeys(benchmark.getAnnotations().get(1).getGoldSpecification()).toString());
        }
        if (first.isEmpty()) return 0;
        double agreement = 0;
        Set<String> labels = new HashSet<>(); labels.addAll(first); labels.addAll(second);
        for (int i = 0; i < first.size(); i++) if (first.get(i).equals(second.get(i))) agreement++;
        double observed = agreement / first.size();
        double expected = 0;
        for (String label : labels) {
            long left = first.stream().filter(label::equals).count();
            long right = second.stream().filter(label::equals).count();
            expected += ((double) left / first.size()) * ((double) right / second.size());
        }
        return Math.abs(1 - expected) < 1e-9 ? (observed == 1 ? 1 : 0) : (observed - expected) / (1 - expected);
    }
}
