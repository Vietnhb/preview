package com.example.backend.service.problem;

import com.example.backend.dto.problem.PageResponse;
import com.example.backend.dto.problem.ProblemResponse;
import com.example.backend.dto.problem.ProblemSummaryResponse;
import com.example.backend.entity.account.User;
import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.SourceAsset;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.problem.ProblemSubmissionRepository;
import com.example.backend.repository.problem.SourceAssetRepository;
import com.example.backend.service.account.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
    public SourceAsset requireOwnedAsset(UUID id) {
        return assets.findByIdAndSubmissionOwner(id, currentUser.requireCurrentUser())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Source asset not found"));
    }

    private ProblemSubmission requireOwnedProblem(UUID id) {
        return problems.findByIdAndOwner(id, currentUser.requireCurrentUser())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Problem not found"));
    }
}
