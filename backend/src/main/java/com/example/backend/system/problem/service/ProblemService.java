package com.example.backend.system.problem.service;

import com.example.backend.base.crud.dto.PageResponse;
import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.problem.dto.ProblemResponse;
import com.example.backend.system.problem.dto.ProblemSummaryResponse;
import com.example.backend.system.problem.dto.SourceAssetResponse;
import com.example.backend.system.problem.mapper.ProblemResponseMapper;
import com.example.backend.system.problem.model.entity.ProblemSubmission;
import com.example.backend.system.problem.repository.ProblemSubmissionRepository;
import com.example.backend.system.problem.repository.SourceAssetRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to records created by the retired extraction workflow. */
@Service
public class ProblemService {
    private final ProblemSubmissionRepository problems;
    private final SourceAssetRepository assets;
    private final ProblemResponseMapper mapper;
    private final CurrentUserService currentUser;

    public ProblemService(ProblemSubmissionRepository problems, SourceAssetRepository assets,
            ProblemResponseMapper mapper, CurrentUserService currentUser) {
        this.problems = problems;
        this.assets = assets;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProblemSummaryResponse> history(int page, int size) {
        User owner = currentUser.requireCurrentUser();
        return PageResponse.from(problems.findByOwnerOrderByCreatedAtDesc(owner,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100))).map(mapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(UUID id) {
        return mapper.toResponse(requireOwnedProblem(id));
    }

    @Transactional(readOnly = true)
    public SourceAssetResponse.Content assetContent(UUID id) {
        var asset = assets.findByIdAndSubmissionOwner(id, currentUser.requireCurrentUser())
                .orElseThrow(() -> ApiException.notFound("Source asset not found"));
        return new SourceAssetResponse.Content(asset.getContentType(), asset.getOriginalFilename(),
                asset.getContentLength(), asset.getContent());
    }

    private ProblemSubmission requireOwnedProblem(UUID id) {
        return problems.findByIdAndOwner(id, currentUser.requireCurrentUser())
                .orElseThrow(() -> ApiException.notFound("Problem not found"));
    }
}
