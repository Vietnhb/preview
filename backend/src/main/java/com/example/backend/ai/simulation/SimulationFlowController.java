package com.example.backend.ai.simulation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.ai.simulation.SimulationFlowResponse.Validation;

@RestController
@RequestMapping({"/api/simulation-flow", "/api/matter-flow"})
public class SimulationFlowController {
    private final SimulationFlowService flow;

    public SimulationFlowController(SimulationFlowService flow) { this.flow = flow; }

    @PostMapping("/normalize")
    public SimulationFlowResponse normalize(@RequestBody NormalizeRequest request) {
        return flow.normalize(request == null ? null : request.sourceMode(),
                request == null ? null : request.text());
    }

    @PostMapping(path = "/normalize-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SimulationFlowResponse normalizeImage(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String text) {
        return flow.normalizeImage(file, text);
    }

    @PostMapping("/{id}/confirm-input")
    public SimulationFlowResponse confirmInput(@PathVariable UUID id, @RequestBody DecisionRequest request) {
        return flow.confirmInput(id, request != null && request.confirmed(),
                request == null ? null : request.correctedText());
    }

    @PostMapping("/{id}/revise")
    public SimulationFlowResponse revise(@PathVariable UUID id, @RequestBody RevisionRequest request) {
        return flow.revise(id, request == null ? null : request.text());
    }

    @PostMapping("/{id}/confirm-explanation")
    public SimulationFlowResponse confirmExplanation(@PathVariable UUID id,
            @RequestBody DecisionRequest request) {
        return flow.confirmExplanation(id, request != null && request.confirmed(),
                request == null ? null : request.text());
    }

    @GetMapping("/{id}/validation")
    public Validation validation(@PathVariable UUID id) { return flow.validation(id); }

    @PostMapping("/{id}/validation")
    public Validation recordValidation(@PathVariable UUID id,
            @RequestBody ValidationRequest request) {
        return flow.recordValidation(id, request == null ? null : request.status(),
                request == null ? null : request.flags(),
                request == null ? null : request.metrics(),
                request == null ? null : request.parameters());
    }

    public record NormalizeRequest(String sourceMode, String text) { }
    public record DecisionRequest(boolean confirmed, String correctedText, String text) { }
    public record RevisionRequest(String text) { }
    public record ValidationRequest(String status, List<String> flags, Map<String, Double> metrics,
            Map<String, Double> parameters) { }
}
