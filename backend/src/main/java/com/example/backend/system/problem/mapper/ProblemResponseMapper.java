package com.example.backend.system.problem.mapper;

import com.example.backend.system.problem.dto.SpecificationResponse;
import com.example.backend.system.problem.model.entity.Specification;
import org.springframework.stereotype.Component;

/** Maps a stored specification to the document embedded in exports. */
@Component
public class ProblemResponseMapper {

    public SpecificationResponse toSpecification(Specification specification) {
        return new SpecificationResponse(
                specification.getId(), specification.getContractVersion(),
                specification.getSchemaVersion(),
                specification.getTopic(),
                specification.getConfidence(),
                specification.getObjects(),
                specification.getQuantities(),
                specification.getRelations(),
                specification.getEndCondition(),
                java.util.List.of(),
                specification.getAmbiguity(),
                specification.getConfirmationState(),
                specification.getSchemaId(),
                specification.getValidationStatus(),
                specification.getValidationResult(),
                specification.getCreatedAt());
    }
}
