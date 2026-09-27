package com.example.backend.service.problem;

import com.example.backend.entity.problem.Specification;
import com.example.backend.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/** The previous post-extraction ambiguity flow is retired; understanding now happens in one simulation request. */
@Component
public class AmbiguityResolutionApplier {
    public void applyAll(Specification specification, Map<String, String> answers) {
        throw new ApiException(HttpStatus.GONE,
                "Legacy specification clarification is retired. Submit the corrected description to /api/simulation/understand.",
                "LEGACY_CLARIFICATION_RETIRED", "SIMULATION");
    }
}
