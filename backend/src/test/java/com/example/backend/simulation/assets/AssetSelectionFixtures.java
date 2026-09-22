package com.example.backend.simulation.assets;

import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.Specification;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A persisted visual decision for physics tests that do not exercise routing. */
public final class AssetSelectionFixtures {
    private AssetSelectionFixtures() { }

    public static void approve(Specification specification, ObjectMapper mapper) {
        var submission = new ProblemSubmission();
        submission.setEditableText("Physics fixture");
        specification.setSubmission(submission);
        specification.setObjects(mapper.createArrayNode());
        var selection = mapper.createObjectNode().put("status", "READY");
        selection.put("sourceFingerprint", AssetSelectionService.fingerprint(specification.getSchemaId(),
                specification.getSchemaVersion(), submission.getEditableText(), specification.getObjects()));
        selection.set("visualization", mapper.createObjectNode().put("scene", "fixture"));
        specification.setAssetSelection(selection);
    }
}
