package com.example.backend.system.reviewer.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.reviewer.dto.EvaluationContracts.*;
import com.example.backend.system.reviewer.dto.EvaluationContracts;
import com.example.backend.system.reviewer.model.entity.EvaluationRun;
import com.example.backend.system.reviewer.repository.EvaluationRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Reads persisted reports from the retired evaluation pipeline. */
@Service
@RequiredArgsConstructor
public class EvaluationService {
    private static final String CONFIRM_FLOW = "confirmFlow";
    private static final String NUMERIC_AGREEMENT = "numericAgreement";
    private static final String INCORRECT_SIMULATION_RATE = "incorrectSimulationRate";
    private static final String PRECISION = "precision";
    private static final String RECALL = "recall";
    private static final String EVALUATION_NOT_FOUND = "Evaluation run not found";
    private final EvaluationRunRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RunPage history(String status, Pageable pageable) {
        Page<EvaluationRun> page = evaluationRepository.search(StringUtils.hasText(status) ? status.trim() : null, pageable);
        return new RunPage(page.getContent().stream().map(RunView::from).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RunView detail(UUID id) {
        return RunView.from(evaluationRepository.findById(id)
                .orElseThrow(() -> com.example.backend.exception.ApiException.notFound(EVALUATION_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public RunComparison compare(UUID baselineId, UUID candidateId) {
        RunView baseline = loadRunView(baselineId);
        RunView candidate = loadRunView(candidateId);
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put(PRECISION, numericDelta(baseline.metrics(), candidate.metrics(), CONFIRM_FLOW, PRECISION));
        delta.put(RECALL, numericDelta(baseline.metrics(), candidate.metrics(), CONFIRM_FLOW, RECALL));
        delta.put("f1", numericDelta(baseline.metrics(), candidate.metrics(), "confirmFlow", "f1"));
        delta.put(NUMERIC_AGREEMENT, numericDelta(baseline.metrics(), candidate.metrics(), null, NUMERIC_AGREEMENT));
        delta.put(INCORRECT_SIMULATION_RATE, numericDelta(baseline.metrics(), candidate.metrics(), null, INCORRECT_SIMULATION_RATE));
        return new RunComparison(baseline, candidate, delta);
    }

    private RunView loadRunView(UUID id) {
        return RunView.from(evaluationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(EVALUATION_NOT_FOUND)));
    }

    private double numericDelta(JsonNode baseline, JsonNode candidate, String parent, String field) {
        JsonNode left = parent == null ? baseline.path(field) : baseline.path(parent).path(field);
        JsonNode right = parent == null ? candidate.path(field) : candidate.path(parent).path(field);
        return right.asDouble(0.0) - left.asDouble(0.0);
    }

}
