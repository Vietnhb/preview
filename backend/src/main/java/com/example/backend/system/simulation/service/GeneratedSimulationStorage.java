package com.example.backend.system.simulation.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.curriculum.model.entity.Lesson;
import com.example.backend.system.curriculum.repository.LessonRepository;
import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.library.dto.LibraryRequests.Save;
import com.example.backend.system.library.dto.LibraryRequests;
import com.example.backend.system.library.model.enums.Visibility;
import com.example.backend.system.library.service.LibraryService;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.problem.model.entity.ExtractionRun;
import com.example.backend.system.problem.model.entity.ProblemSubmission;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.problem.model.enums.ConfirmationState;
import com.example.backend.system.problem.model.enums.ExtractionPath;
import com.example.backend.system.problem.model.enums.ExtractionRunStatus;
import com.example.backend.system.problem.model.enums.SourceMode;
import com.example.backend.system.problem.model.enums.SubmissionStatus;
import com.example.backend.system.simulation.dto.SimulationRequests;
import com.example.backend.system.simulation.model.entity.Simulation;
import com.example.backend.system.simulation.model.entity.SimulationRun;
import com.example.backend.system.simulation.model.enums.SimulationStatus;
import com.example.backend.system.simulation.repository.SimulationRepository;
import com.example.backend.system.simulation.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public LibraryItemResponse save(SimulationRequests.Save request, SchemaVersion schema, ObjectNode computed) {
        var user = currentUser.requireCurrentUser();
        var lesson = lessons.findById(request.lessonId())
                .orElseThrow(() -> ApiException.notFound("Lesson not found"));
        if (!lesson.isActive() || !lesson.getLevel().isActive() || !lesson.getLevel().getModule().isActive()
                || !lesson.getLevel().getModule().getTopic().isEnabled())
            throw ApiException.conflict("Bài học này không còn được sử dụng");
        if (!lesson.getLevel().getModule().getTopic().getName().equalsIgnoreCase(schema.getTopic()))
            throw ApiException.conflict("Bài học không thuộc chủ đề của mô phỏng");
        String status = computed.path("validation").path("status").asText();
        if (!status.equals("VERIFIED_ANALYTICAL") && !status.equals("VERIFIED_NUMERICAL"))
            throw ApiException.conflict("Chỉ lưu mô phỏng đã được kiểm chứng");
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
        return library.save(new LibraryRequests.Save(simulation.getId(), request.folderId(), request.lessonId(), request.title(), Visibility.PERSONAL));
    }

    @Transactional(readOnly = true)
    public ObjectNode open(UUID id) {
        var user = currentUser.requireCurrentUser();
        var simulation = simulations.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Simulation not found"));
        var run = runs.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())
                .orElseThrow(() -> ApiException.notFound("Không có dữ liệu mô phỏng đã lưu"));
        if (!run.getResult().path("simulationSpec").path("visualProgram").isObject())
            throw ApiException.conflict("Bài lưu cũ chưa có cảnh SVG/PixiJS để mở trong trình mô phỏng này");
        return ((ObjectNode) run.getResult()).deepCopy();
    }

    @Transactional
    public void updateVisual(UUID id, ObjectNode generated) {
        var user = currentUser.requireCurrentUser();
        var simulation = simulations.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Simulation not found"));
        var previous = runs.findFirstBySimulationIdOrderByCreatedAtDesc(id)
                .orElseThrow(() -> ApiException.notFound("Không có dữ liệu mô phỏng đã lưu"));
        ObjectNode snapshot = ((ObjectNode) previous.getResult()).deepCopy();
        for (String key : java.util.List.of("schemaId", "schemaVersion", "description", "planSignature")) {
            if (!snapshot.path(key).equals(generated.path(key)))
                throw ApiException.conflict("Thiết kế mới không thuộc kế hoạch vật lý của bài đã lưu");
        }
        var program = generated.path("simulationSpec").path("visualProgram");
        if (!program.isObject() || (program.path("code").asText().isBlank() && !program.path("scene").isObject()))
            throw ApiException.badRequest("Thiếu cảnh minh họa mới");
        ObjectNode spec = (ObjectNode) snapshot.path("simulationSpec");
        spec.set("visualProgram", program.deepCopy());
        snapshot.put("code", program.path("code").asText(""));
        var design = generated.path("simulationSpec").get("visualDesign");
        if (design == null) spec.remove("visualDesign"); else spec.set("visualDesign", design.deepCopy());
        // Preserve pinned historical runs and all saved physics/parameter data.
        var next = new SimulationRun();
        org.springframework.beans.BeanUtils.copyProperties(previous, next, "id", "createdAt", "updatedAt", "result");
        next.setResult(snapshot);
        entities.persist(next);
        simulation.setLatestRun(next);
        entities.flush();
    }
}
