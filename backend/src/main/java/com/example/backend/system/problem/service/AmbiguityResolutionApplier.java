package com.example.backend.system.problem.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.problem.model.entity.Specification;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The previous post-extraction ambiguity flow is retired; understanding now happens in one simulation request. */
@Component
public class AmbiguityResolutionApplier {
    public void applyAll(Specification specification, Map<String, String> answers) {
        throw ApiException.gone("Legacy specification clarification is retired. Submit the corrected description to /api/simulation/understand.",
                "LEGACY_CLARIFICATION_RETIRED", "SIMULATION");
    }
}
