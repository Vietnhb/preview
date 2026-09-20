package com.example.backend.controller.admin;

import com.example.backend.schema.routing.index.SchemaEmbeddingIndexer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrative, idempotent refresh for the derived schema retrieval index. */
@RestController
@RequestMapping("/api/admin/schema-routing")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SchemaRoutingAdminController {
    private final SchemaEmbeddingIndexer indexer;

    public record ReindexRequest(@NotNull Boolean dryRun) { }

    @PostMapping("/reindex")
    public SchemaEmbeddingIndexer.ReindexResult reindex(@Valid @RequestBody ReindexRequest request) {
        return indexer.reindex(request.dryRun());
    }
}
