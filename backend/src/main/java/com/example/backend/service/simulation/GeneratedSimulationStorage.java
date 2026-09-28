package com.example.backend.service.simulation;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.dto.library.LibrarySaveRequest;
import com.example.backend.dto.simulation.SaveGeneratedSimulationRequest;
import com.example.backend.entity.enums.*;
import com.example.backend.entity.problem.*;
import com.example.backend.entity.simulation.*;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.curriculum.LessonRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.library.LibraryService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

/** Stores the generated scene and verified timeline in the existing library/run model. */
@Service
@RequiredArgsConstructor
public class GeneratedSimulationStorage {
    private final EntityManager entities;
    private final CurrentUserService currentUser;
    private final LessonRepository lessons;
    private final SimulationRepository simulations;
    private final SimulationRunRepository runs;
    private final LibraryService library;

    @Transactional
    public LibraryItemResponse save(SaveGeneratedSimulationRequest request, SchemaVersion schema, ObjectNode computed) {
        var user = currentUser.requireCurrentUser();
        var lesson = lessons.findById(request.lessonId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lesson not found"));
        if (!lesson.isActive() || !lesson.getLevel().isActive() || !lesson.getLevel().getModule().isActive()
                || !lesson.getLevel().getModule().getTopic().isEnabled())
            throw new ApiException(HttpStatus.CONFLICT, "Bài học này không còn được sử dụng");
        if (!lesson.getLevel().getModule().getTopic().getName().equalsIgnoreCase(schema.getTopic()))
            throw new ApiException(HttpStatus.CONFLICT, "Bài học không thuộc chủ đề của mô phỏng");
        String status = computed.path("validation").path("status").asText();
        if (!status.equals("VERIFIED_ANALYTICAL") && !status.equals("VERIFIED_NUMERICAL"))
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ lưu mô phỏng đã được kiểm chứng");
        ObjectNode snapshot = request.simulation().deepCopy();
        ObjectNode spec = (ObjectNode) snapshot.path("simulationSpec");
        spec.put("topic", schema.getTopic());
        spec.set("solverTimeline", computed.path("solverTimeline"));
        snapshot.set("parameters", spec.path("parameters").deepCopy());
        snapshot.set("validation", computed.path("validation"));
        ObjectNode savedParameters = snapshot.putObject("savedParameters");
        for (var parameter : spec.path("parameters")) {
            String name = parameter.path("name").asText();
            savedParameters.set(name, request.parameters().has(name) ? request.parameters().get(name).deepCopy() : parameter.path("value").deepCopy());
        }

        var submission = new ProblemSubmission();
        submission.setOwner(user);
        submission.setLesson(lesson);
        submission.setSourceMode(SourceMode.TEXT);
        submission.setOriginalText(snapshot.path("description").asText());
        submission.setEditableText(submission.getOriginalText());
        submission.setStatus(SubmissionStatus.READY_FOR_VALIDATION);
        entities.persist(submission);
        var extraction = new ExtractionRun();
        extraction.setSubmission(submission);
        extraction.setExtractionPath(ExtractionPath.AI_PROVIDER);
        extraction.setProviderName("schema-driven-simulation");
        extraction.setStatus(ExtractionRunStatus.SUCCEEDED);
        entities.persist(extraction);
        var specification = new Specification();
        specification.setSubmission(submission);
        specification.setExtractionRun(extraction);
        specification.setSchemaId(schema.getSchemaId());
        specification.setSchemaVersion(schema.getVersion());
        specification.setTopic(schema.getTopic());
        specification.setConfidence(BigDecimal.ONE);
        specification.setObjects(spec.path("physicsModels").deepCopy());
        specification.setQuantities(spec.path("parameters").deepCopy());
        specification.setRelations(snapshot.arrayNode());
        specification.setAmbiguity(snapshot.arrayNode());
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        specification.setValidationStatus("PASSED");
        specification.setValidationResult(computed.path("validation").deepCopy());
        entities.persist(specification);
        submission.setCurrentSpecification(specification);
        var simulation = new Simulation();
        simulation.setOwner(user);
        simulation.setSpecification(specification);
        simulation.setSchemaId(schema.getSchemaId());
        simulation.setSolverVersion("schema-equations");
        simulation.setStatus(SimulationStatus.READY);
        entities.persist(simulation);
        var run = new SimulationRun();
        run.setSimulation(simulation);
        run.setRunType("GENERATED");
        run.setSchemaId(schema.getSchemaId());
        run.setSchemaVersion(schema.getVersion());
        run.setValidationPassed(true);
        run.setValidationCheckpoints(computed.path("validation").deepCopy());
        run.setDurationSeconds(computed.path("solverTimeline").path("durationSeconds").asDouble(spec.path("durationSeconds").asDouble()));
        run.setResult(snapshot);
        entities.persist(run);
        simulation.setLatestRun(run);
        entities.flush();
        return library.save(new LibrarySaveRequest(simulation.getId(), request.folderId(), request.lessonId(), request.title(), Visibility.PERSONAL));
    }

    @Transactional(readOnly = true)
    public ObjectNode open(UUID id) {
        var user = currentUser.requireCurrentUser();
        var simulation = simulations.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Simulation not found"));
        var run = runs.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không có dữ liệu mô phỏng đã lưu"));
        if (!run.getResult().path("simulationSpec").path("visualProgram").isObject())
            throw new ApiException(HttpStatus.CONFLICT, "Bài lưu cũ chưa có cảnh SVG/PixiJS để mở trong trình mô phỏng này");
        return ((ObjectNode) run.getResult()).deepCopy();
    }
}
