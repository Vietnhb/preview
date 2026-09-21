package com.example.backend.controller.admin;

import com.example.backend.schema.routing.index.SchemaEmbeddingIndexer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class SchemaRoutingAdminControllerContractTest {
    private final SchemaEmbeddingIndexer indexer = mock(SchemaEmbeddingIndexer.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new SchemaRoutingAdminController(indexer)).build();

    @Test
    void dryRunIsValidatedDelegatedAndReturnedAsTypedResult() throws Exception {
        when(indexer.reindex(true)).thenReturn(new SchemaEmbeddingIndexer.ReindexResult(
                true, 74, 70, 4, "fake-provider", "fake-model", 3));

        mvc.perform(post("/api/admin/schema-routing/reindex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.approvedSchemas").value(74))
                .andExpect(jsonPath("$.currentEmbeddings").value(70))
                .andExpect(jsonPath("$.pendingEmbeddings").value(4))
                .andExpect(jsonPath("$.embeddingProvider").value("fake-provider"))
                .andExpect(jsonPath("$.embeddingModel").value("fake-model"))
                .andExpect(jsonPath("$.embeddingDimension").value(3));

        verify(indexer).reindex(true);
        verifyNoMoreInteractions(indexer);
    }

    @Test
    void missingDryRunIsRejectedBeforeTheIndexerCanMutateAnything() throws Exception {
        mvc.perform(post("/api/admin/schema-routing/reindex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoMoreInteractions(indexer);
    }
}
