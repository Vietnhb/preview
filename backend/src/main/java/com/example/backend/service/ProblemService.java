package com.example.backend.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.dto.problem.CreateProblemRequest;
import com.example.backend.dto.problem.PageResponse;
import com.example.backend.dto.problem.ProblemResponse;
import com.example.backend.dto.problem.ProblemSummaryResponse;
import com.example.backend.entity.AmbiguityCase;
import com.example.backend.entity.AmbiguityStatus;
import com.example.backend.entity.AssetType;
import com.example.backend.entity.ConfirmationState;
import com.example.backend.entity.ExtractionOutcome;
import com.example.backend.entity.ExtractionRun;
import com.example.backend.entity.ExtractionRunStatus;
import com.example.backend.entity.Lesson;
import com.example.backend.entity.OcrStatus;
import com.example.backend.entity.ProblemSubmission;
import com.example.backend.entity.SourceAsset;
import com.example.backend.entity.SourceMode;
import com.example.backend.entity.Specification;
import com.example.backend.entity.SubmissionStatus;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.extraction.AmbiguityItem;
import com.example.backend.extraction.ExtractionCoordinator;
import com.example.backend.extraction.ExtractionResult;
import com.example.backend.extraction.OcrProvider;
import com.example.backend.extraction.OcrResult;
import com.example.backend.extraction.SpecificationDocument;
import com.example.backend.repository.ExtractionRunRepository;
import com.example.backend.repository.LessonRepository;
import com.example.backend.repository.ProblemSubmissionRepository;
import com.example.backend.repository.SourceAssetRepository;
import com.example.backend.repository.SpecificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private static final long MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final ProblemSubmissionRepository problemRepository;
    private final SourceAssetRepository sourceAssetRepository;
    private final ExtractionRunRepository extractionRunRepository;
    private final SpecificationRepository specificationRepository;
    private final LessonRepository lessonRepository;
    private final CurrentUserService currentUserService;
    private final ProblemResponseMapper mapper;
    private final ExtractionCoordinator extractionCoordinator;
    private final OcrProvider ocrProvider;
    private final ObjectMapper objectMapper;
    private final AmbiguityResolutionApplier ambiguityResolutionApplier;
    private final SpecificationReadinessService readinessService;
    private final SchemaDefinitionService schemaDefinitions;

    @Transactional
    public ProblemResponse create(CreateProblemRequest request) {
        if (request == null || !StringUtils.hasText(request.text())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Problem text is required");
        }
        SourceMode mode = request.sourceMode() == null ? SourceMode.TEXT : request.sourceMode();
        if (mode != SourceMode.TEXT && mode != SourceMode.PASTE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use the image endpoint for IMAGE or MIXED input");
        }

        ProblemSubmission problem = new ProblemSubmission();
        problem.setOwner(currentUserService.requireCurrentUser());
        problem.setLesson(resolveLesson(request.lessonId()));
        problem.setSourceMode(mode);
        problem.setOriginalText(request.text().trim());
        problem.setEditableText(request.text().trim());
        problem.setStatus(SubmissionStatus.DRAFT);
        return mapper.toResponse(problemRepository.save(problem));
    }

    @Transactional
    public ProblemResponse createFromImage(MultipartFile file, String suppliedText, UUID lessonId) {
        validateImage(file);
        User owner = currentUserService.requireCurrentUser();
        byte[] content = readContent(file);
        OcrResult ocr = ocrProvider.recognize(file.getContentType(), content);

        ProblemSubmission problem = new ProblemSubmission();
        problem.setOwner(owner);
        problem.setLesson(resolveLesson(lessonId));
        problem.setSourceMode(StringUtils.hasText(suppliedText) ? SourceMode.MIXED : SourceMode.IMAGE);
        problem.setOriginalText(trimToNull(suppliedText));
        problem.setEditableText(ocr.status() == OcrStatus.SUCCEEDED ? ocr.text() : trimToNull(suppliedText));
        problem.setStatus(ocr.status() == OcrStatus.SUCCEEDED
                ? SubmissionStatus.OCR_PREVIEW_READY
                : SubmissionStatus.DRAFT);

        SourceAsset asset = new SourceAsset();
        asset.setAssetType(AssetType.IMAGE);
        asset.setOriginalFilename(safeFilename(file.getOriginalFilename()));
        asset.setContentType(file.getContentType());
        asset.setContentLength(content.length);
        asset.setChecksum(sha256(content));
        asset.setContent(content);
        asset.setOcrStatus(ocr.status());
        asset.setOcrText(ocr.text());
        asset.setOcrError(ocr.errorMessage());
        problem.addSourceAsset(asset);

        return mapper.toResponse(problemRepository.save(problem));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProblemSummaryResponse> history(int page, int size) {
        User owner = currentUserService.requireCurrentUser();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.from(problemRepository.findByOwnerOrderByCreatedAtDesc(
                owner, PageRequest.of(safePage, safeSize)).map(mapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(UUID id) {
        return mapper.toResponse(requireOwnedProblem(id));
    }

    @Transactional
    public ProblemResponse updateText(UUID id, String text) {
        if (!StringUtils.hasText(text)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Problem text is required");
        }
        ProblemSubmission problem = requireOwnedProblem(id);
        problem.setEditableText(text.trim());
        problem.setStatus(SubmissionStatus.DRAFT);
        return mapper.toResponse(problem);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ProblemResponse extract(UUID id) {
        ProblemSubmission problem = requireOwnedProblem(id);
        if (!StringUtils.hasText(problem.getEditableText())) {
            throw new ApiException(HttpStatus.CONFLICT, "Confirm or enter OCR text before extraction");
        }

        ExtractionRun run = new ExtractionRun();
        run.setSubmission(problem);
        run.setExtractionPath(com.example.backend.entity.ExtractionPath.OPENROUTER);
        run.setProviderName("pending");
        run.setStatus(ExtractionRunStatus.RUNNING);
        extractionRunRepository.save(run);

        try {
            ExtractionResult result = extractionCoordinator.extract(problem.getEditableText());
            applyResult(run, result);
            Specification specification = createSpecification(problem, run, result.document());
            readinessService.ensureRequiredAmbiguities(specification);
            specificationRepository.save(specification);
            problem.setCurrentSpecification(specification);
            problem.setStatus(specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                    ? SubmissionStatus.NEEDS_CONFIRMATION
                    : SubmissionStatus.READY_FOR_VALIDATION);
            return mapper.toResponse(problem);
        } catch (RuntimeException exception) {
            run.setStatus(ExtractionRunStatus.FAILED);
            run.setOutcome(ExtractionOutcome.FAILED);
            run.setErrorMessage("AI extraction failed");
            problem.setStatus(SubmissionStatus.FAILED);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "AI problem understanding failed; specification was not created. Cause: " + safeCause(exception));
        }
    }

    @Transactional
    public ProblemResponse confirm(UUID id, Map<String, String> answers) {
        ProblemSubmission problem = requireOwnedProblem(id);
        Specification specification = problem.getCurrentSpecification();
        if (specification == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Extract a specification before confirming it");
        }
        Map<String, String> safeAnswers = answers == null ? Map.of() : answers;
        for (AmbiguityCase ambiguity : specification.getAmbiguityCases()) if (ambiguity.getStatus() == AmbiguityStatus.OPEN
                && !StringUtils.hasText(safeAnswers.get(ambiguity.getCode()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Answer every ambiguity before confirming");
        }
        try { ambiguityResolutionApplier.applyAll(specification, safeAnswers); }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_GATEWAY,
                "AI ambiguity confirmation failed; no changes were saved. Cause: " + safeCause(exception)); }
        readinessService.ensureRequiredAmbiguities(specification);
        problem.setStatus(specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                ? SubmissionStatus.NEEDS_CONFIRMATION : SubmissionStatus.READY_FOR_VALIDATION);
        return mapper.toResponse(problem);
    }

    @Transactional(readOnly = true)
    public SourceAsset requireOwnedAsset(UUID assetId) {
        User owner = currentUserService.requireCurrentUser();
        return sourceAssetRepository.findByIdAndSubmissionOwner(assetId, owner)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Source asset not found"));
    }

    private Specification createSpecification(ProblemSubmission problem, ExtractionRun run,
            SpecificationDocument document) {
        Specification specification = new Specification();
        specification.setSubmission(problem);
        specification.setExtractionRun(run);
        specification.setContractVersion(SpecificationDocument.CURRENT_SCHEMA_VERSION);
        specification.setSchemaVersion(schemaDefinitions.requireApproved(document.schemaId()).getVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId());
        specification.setConfidence(document.confidence());
        specification.setObjects(objectMapper.valueToTree(document.objects()));
        specification.setQuantities(objectMapper.valueToTree(document.quantities()));
        specification.setRelations(objectMapper.valueToTree(document.relations()));
        specification.setAmbiguity(objectMapper.valueToTree(document.ambiguities()));
        specification.setConfirmationState(document.ambiguities().isEmpty()
                ? ConfirmationState.NO_AMBIGUITY
                : ConfirmationState.UNRESOLVED);
        for (AmbiguityItem item : document.ambiguities()) {
            AmbiguityCase ambiguity = new AmbiguityCase();
            ambiguity.setCode(item.code());
            ambiguity.setFieldPath(item.fieldPath());
            ambiguity.setQuestion(item.question());
            ambiguity.setOptions(objectMapper.valueToTree(item.options()));
            ambiguity.setStatus(AmbiguityStatus.OPEN);
            specification.addAmbiguityCase(ambiguity);
        }
        return specification;
    }

    private void applyResult(ExtractionRun run, ExtractionResult result) {
        run.setExtractionPath(result.path());
        run.setProviderName(result.providerName());
        run.setModelVersion(result.modelVersion());
        run.setStatus(ExtractionRunStatus.SUCCEEDED);
        run.setOutcome(result.outcome());
        run.setRawResponse(result.rawResponse());
        run.setErrorMessage(result.errorMessage());
    }

    private ProblemSubmission requireOwnedProblem(UUID id) {
        User owner = currentUserService.requireCurrentUser();
        return problemRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Problem not found"));
    }

    private Lesson resolveLesson(UUID lessonId) {
        if (lessonId == null) {
            return null;
        }
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Lesson not found"));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must not exceed 10 MB");
        }
        if (!IMAGE_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Supported images: PNG, JPEG, WebP, GIF");
        }
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot read image file");
        }
    }

    private String safeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "image";
        }
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        if (!StringUtils.hasText(message)) return current.getClass().getSimpleName();
        String sanitized = message.replaceAll("(?i)bearer\\s+[^\\s,]+", "Bearer [redacted]");
        return sanitized.substring(0, Math.min(240, sanitized.length()));
    }
}
