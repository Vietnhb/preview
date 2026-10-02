package com.example.backend.system.simulation.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.curriculum.repository.LessonRepository;
import com.example.backend.system.library.service.LibraryService;
import com.example.backend.system.simulation.model.entity.Simulation;
import com.example.backend.system.simulation.model.entity.SimulationRun;
import com.example.backend.system.simulation.repository.SimulationRepository;
import com.example.backend.system.simulation.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneratedVisualUpdateTest {
    @Test
    void redesignCreatesNewRunWithoutChangingSavedPhysicsOrPreviousRun() {
        var em = mock(EntityManager.class);
        var current = mock(CurrentUserService.class);
        var simulations = mock(SimulationRepository.class);
        var runs = mock(SimulationRunRepository.class);
        var service = new GeneratedSimulationStorage(em, current, mock(LessonRepository.class), simulations, runs, mock(LibraryService.class));
        var user = new User(); user.setId(123);
        var simulation = new Simulation(); simulation.setId(UUID.randomUUID());
        when(current.requireCurrentUser()).thenReturn(user);
        when(simulations.findByIdAndOwnerId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        var json = new ObjectMapper();
        var snapshot = json.createObjectNode().put("schemaId", "motion").put("schemaVersion", "1").put("description", "Car").put("planSignature", "signed").put("code", "old");
        snapshot.putObject("savedParameters").put("speed", 8);
        var spec = snapshot.putObject("simulationSpec");
        spec.putObject("solverTimeline").put("durationSeconds", 7);
        spec.putObject("visualProgram").put("code", "old");
        var old = new SimulationRun(); old.setId(UUID.randomUUID()); old.setSimulation(simulation); old.setResult(snapshot); old.setDurationSeconds(7); old.setValidationPassed(true);
        when(runs.findFirstBySimulationIdOrderByCreatedAtDesc(simulation.getId())).thenReturn(Optional.of(old));
        var generated = snapshot.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) generated.path("simulationSpec").path("visualProgram")).put("code", "new");
        generated.putObject("savedParameters").put("speed", 999);
        service.updateVisual(simulation.getId(), generated);
        var captured = ArgumentCaptor.forClass(SimulationRun.class);
        verify(em).persist(captured.capture());
        var next = captured.getValue();
        assertNull(next.getId());
        assertEquals("new", next.getResult().path("code").asText());
        assertEquals(8, next.getResult().path("savedParameters").path("speed").asInt());
        assertEquals(spec.path("solverTimeline"), next.getResult().path("simulationSpec").path("solverTimeline"));
        assertEquals("old", old.getResult().path("code").asText());
        assertTrue(next.isValidationPassed());
        assertSame(next, simulation.getLatestRun());
        generated.put("planSignature", "different");
        assertThrows(ApiException.class, () -> service.updateVisual(simulation.getId(), generated));
        verify(em, times(1)).persist(any());
    }
}
