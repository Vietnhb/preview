package com.example.backend.controller.simulation;

import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.simulation.GeneratedSimulationStorage;
import com.example.backend.service.simulation.SchemaEquationRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GeneratedSimulationEndpointsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
    private final SchemaEquationRuntime equations = mock(SchemaEquationRuntime.class);
    private final GeneratedSimulationStorage storage = mock(GeneratedSimulationStorage.class);
    private SimulationUnderstandingController controller;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        controller = mock(SimulationUnderstandingController.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(controller, "json", json);
        ReflectionTestUtils.setField(controller, "schemas", schemas);
        ReflectionTestUtils.setField(controller, "equations", equations);
        ReflectionTestUtils.setField(controller, "storage", storage);
        ReflectionTestUtils.setField(controller, "signingKey", "endpoint-test-key".getBytes(StandardCharsets.UTF_8));
        when(schemas.compiledChecksum(any())).thenAnswer(call -> call.getArgument(0).toString());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void saveRecomputesSignedPlanWithCurrentParametersBeforeStoring() throws Exception {
        ObjectNode simulation = json.createObjectNode().put("schemaId", "motion").put("schemaVersion", "1.0").put("description", "Projectile");
        ObjectNode spec = simulation.putObject("simulationSpec").put("durationSeconds", 2);
        simulation.put("planSignature", (String) ReflectionTestUtils.invokeMethod(controller, "signPlan", simulation));
        ObjectNode params = json.createObjectNode().put("height", 20);
        var schema = new SchemaVersion();
        schema.setVersion("1.0"); schema.setDefinition(json.createObjectNode());
        when(schemas.requireCurrentApproved("motion", "1.0")).thenReturn(schema);
        ObjectNode computed = json.createObjectNode();
        computed.putObject("validation").put("status", "VERIFIED_ANALYTICAL");
        when(equations.compute(eq(schema.getDefinition()), eq(spec), eq(params))).thenReturn(computed);
        ObjectNode request = json.createObjectNode().put("title", "Test").put("folderId", UUID.randomUUID().toString()).put("lessonId", UUID.randomUUID().toString());
        request.set("simulation", simulation); request.set("parameters", params);

        mvc.perform(post("/api/simulation/saved").contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isOk());
        verify(storage).save(any(), same(schema), same(computed));

        spec.put("durationSeconds", 3);
        mvc.perform(post("/api/simulation/saved").contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isConflict());
        verifyNoMoreInteractions(storage);
    }

    @Test
    void savedSimulationCanBeReopenedThroughStorageOwnershipChecks() throws Exception {
        UUID id = UUID.randomUUID();
        when(storage.open(id)).thenReturn(json.createObjectNode().put("description", "Saved projectile"));
        mvc.perform(get("/api/simulation/saved/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Saved projectile"));
        verify(storage).open(id);
    }
}
