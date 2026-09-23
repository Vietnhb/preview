package com.example.backend.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AssetRequestSummaryValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOnePinnedAssetRequestForEveryObjectAndRendererTarget() throws Exception {
        var specification = mapper.readTree("""
                {"objects":[{"id":"body-1"}],"visualTargets":[
                  {"targetId":"/presentation/actors/0","kind":"actor"}]}
                """);
        var summary = mapper.readTree("""
                {"assetRequests":[{"targetId":"/presentation/actors/0","objectId":"body-1",
                  "kind":"actor","requestedDescription":"Generic moving object; appearance unspecified."}]}
                """);

        var validated = StructuredExtractionProvider.validateAssetRequestSummary(specification, summary);

        assertEquals(summary, validated);
    }

    @Test
    void rejectsUnknownTargetDuplicateTargetAndExtraGeneratedAssetFields() throws Exception {
        var specification = mapper.readTree("""
                {"objects":[{"id":"body-1"}],"visualTargets":[
                  {"targetId":"/presentation/actors/0","kind":"actor"}]}
                """);
        var unknownTarget = mapper.readTree("""
                {"assetRequests":[{"targetId":"/invented","objectId":"body-1","kind":"actor",
                  "requestedDescription":"Generic object."}]}
                """);
        var extraAssetId = mapper.readTree("""
                {"assetRequests":[{"targetId":"/presentation/actors/0","objectId":"body-1","kind":"actor",
                  "requestedDescription":"Generic object.","assetId":"invented"}]}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> StructuredExtractionProvider.validateAssetRequestSummary(specification, unknownTarget));
        assertThrows(IllegalArgumentException.class,
                () -> StructuredExtractionProvider.validateAssetRequestSummary(specification, extraAssetId));
    }
}
