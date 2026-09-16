package com.example.backend.service;

import com.example.backend.entity.*;
import com.example.backend.repository.BenchmarkProblemRepository;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class BenchmarkReviewService {
    private final BenchmarkProblemRepository repository;
    private final CurrentUserService currentUser;
    private final EntityManager entityManager;
    public record CreateRequest(@NotBlank String problemText, @NotBlank @Size(max=32) String topic,
            @NotBlank @Size(max=32) String gradeScope, @NotBlank @Size(max=80) String sourceCategory) { }
    public record AnnotationRequest(@NotNull JsonNode specification) { }
    public record AnnotationView(String actor, JsonNode specification) { }
    public record View(UUID id, String problemText, String topic, String gradeScope, String sourceCategory,
            String status, int annotationCount, boolean canAnnotate, boolean canAdjudicate,
            List<AnnotationView> annotations, JsonNode goldSpecification) { }

    @Transactional(readOnly=true)
    public List<View> list() {
        String actor = actor(); return repository.findAll().stream().map(b -> view(b, actor)).toList();
    }
    @Transactional
    public View create(CreateRequest request) {
        BenchmarkProblem b = new BenchmarkProblem(); b.setProblemText(request.problemText().trim());
        b.setTopic(request.topic().trim()); b.setGradeScope(request.gradeScope()); b.setSourceCategory(request.sourceCategory()); b.setActive(true);
        return view(repository.save(b), actor());
    }
    @Transactional
    public View annotate(UUID id, AnnotationRequest request) {
        validate(request.specification());
        BenchmarkProblem b = locked(id);
        String actor = actor();
        if (!b.getAdjudications().isEmpty() || b.getAnnotations().size() >= 2
                || b.getAnnotations().stream().anyMatch(a -> a.getAnnotatorReference().equals(actor)))
            throw new ApiException(HttpStatus.CONFLICT, "Two independent annotators are required; an annotation cannot be replaced");
        GoldAnnotation annotation = new GoldAnnotation(); annotation.setAnnotatorReference(actor);
        annotation.setAnnotationStatus("SUBMITTED"); annotation.setGoldSpecification(request.specification()); b.addAnnotation(annotation);
        return view(repository.save(b), actor);
    }
    @Transactional
    public View adjudicate(UUID id, AnnotationRequest request) {
        validate(request.specification());
        BenchmarkProblem b = locked(id);
        String actor = actor();
        if (b.getAnnotations().size() != 2 || !b.getAdjudications().isEmpty()
                || b.getAnnotations().stream().anyMatch(a -> a.getAnnotatorReference().equals(actor)))
            throw new ApiException(HttpStatus.CONFLICT, "A third independent reviewer must adjudicate after two annotations");
        Adjudication result = new Adjudication(); result.setReviewerReference(actor);
        result.setResolvedSpecification(request.specification()); result.setDisagreementState("RESOLVED"); b.addAdjudication(result);
        return view(repository.save(b), actor);
    }
    private BenchmarkProblem locked(UUID id) {
        BenchmarkProblem b = entityManager.find(BenchmarkProblem.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (b == null || !b.isActive()) throw new ApiException(HttpStatus.NOT_FOUND, "Active benchmark not found");
        return b;
    }
    private String actor() { return "user:" + currentUser.requireCurrentUser().getId(); }
    private void validate(JsonNode spec) {
        if (spec == null || !spec.isObject() || !spec.path("objects").isArray()
                || !spec.path("quantities").isArray() || !spec.path("relations").isArray())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Specification must contain objects, quantities and relations arrays");
        for (JsonNode quantity : spec.path("quantities")) {
            if (quantity.path("name").asText().isBlank() || !quantity.path("normalizedValue").isNumber()
                    || quantity.path("normalizedUnit").asText().isBlank())
                throw new ApiException(HttpStatus.BAD_REQUEST, "Each quantity requires name, normalizedValue and normalizedUnit");
        }
    }
    private View view(BenchmarkProblem b, String actor) {
        boolean complete = b.getAnnotations().size() >= 2;
        boolean own = b.getAnnotations().stream().anyMatch(a -> a.getAnnotatorReference().equals(actor));
        JsonNode gold = b.getAdjudications().isEmpty() ? null : b.getAdjudications().get(0).getResolvedSpecification();
        boolean agreed = complete && b.getAnnotations().get(0).getGoldSpecification().equals(b.getAnnotations().get(1).getGoldSpecification());
        if (gold == null && agreed) gold = b.getAnnotations().get(0).getGoldSpecification();
        String status = statusFor(gold, complete);
        List<AnnotationView> visible = b.getAnnotations().stream()
                .filter(a -> complete || a.getAnnotatorReference().equals(actor))
                .map(a -> new AnnotationView(a.getAnnotatorReference(), a.getGoldSpecification())).toList();
        return new View(b.getId(), b.getProblemText(), b.getTopic(), b.getGradeScope(), b.getSourceCategory(), status,
                b.getAnnotations().size(), b.isActive() && !complete && !own,
                b.isActive() && complete && !own && gold == null, visible, gold);
    }

    private String statusFor(JsonNode gold, boolean complete) {
        if (gold != null) return "GOLD_READY";
        return complete ? "DISAGREEMENT" : "ANNOTATING";
    }
}
