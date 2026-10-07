package com.example.backend.system.simulation.controller;

import com.example.backend.config.UploadProperties;
import com.example.backend.exception.ApiException;
import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.simulation.dto.SimulationRequests;
import com.example.backend.system.simulation.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class AIController {
    private final AIService ai;
    private final UploadProperties upload;

    @PostMapping(path = "/understand", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode understandText(@Valid @RequestBody SimulationRequests.Understand request) {
        return ai.understandText(request.description(), request.sessionId(), request.recognizedText(), request.correctedText());
    }

    @PostMapping(path = "/understand", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObjectNode understandImage(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String text) {
        if (file.isEmpty() || file.getSize() > upload.maxImageBytes())
            throw ApiException.badRequest("Ảnh trống hoặc vượt quá giới hạn dung lượng");
        try { return ai.understandImage(file.getBytes(), file.getContentType(), text); }
        catch (IOException ex) { throw ApiException.badRequest("Không thể đọc ảnh đã tải lên"); }
    }

    @PostMapping(path = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode generate(@RequestBody JsonNode request) { return ai.generate(request); }

    @PostMapping(path = "/compute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode compute(@RequestBody JsonNode request) { return ai.compute(request); }

    @PostMapping(path = "/saved", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LibraryItemResponse save(@Valid @RequestBody SimulationRequests.Save request) { return ai.save(request); }

    @GetMapping("/saved/{id}")
    public ObjectNode openSaved(@PathVariable UUID id) { return ai.openSaved(id); }

    @PatchMapping("/saved/{id}/visual")
    public void updateSavedVisual(@PathVariable UUID id, @RequestBody ObjectNode generated) { ai.updateSavedVisual(id, generated); }

}
