package com.example.backend.controller.export;

import com.example.backend.service.export.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/exports")
@RequiredArgsConstructor
public class ExportController {
    private final ExportService exportService;

    @GetMapping("/{specificationId}/json")
    public ResponseEntity<byte[]> json(@PathVariable UUID specificationId) {
        return download(exportService.specificationJson(specificationId), "specification.json", MediaType.APPLICATION_JSON);
    }

    @GetMapping("/{specificationId}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable UUID specificationId) {
        return download(exportService.simulationCsv(specificationId), "simulation.csv", MediaType.parseMediaType("text/csv"));
    }

    @GetMapping("/{specificationId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID specificationId) {
        return download(exportService.simulationPdf(specificationId), "simulation-report.pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/{specificationId}/html")
    public ResponseEntity<byte[]> html(@PathVariable UUID specificationId) {
                return download(exportService.offlineReplayHtml(specificationId), "physlive-offline-replay.html", MediaType.TEXT_HTML);
    }

    @GetMapping("/{specificationId}/slides")
    public ResponseEntity<byte[]> slides(@PathVariable UUID specificationId) {
        return download(exportService.slidesHtml(specificationId), "physlive-slides.html", MediaType.TEXT_HTML);
    }

    private ResponseEntity<byte[]> download(byte[] body, String filename, MediaType contentType) {
        return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString()).body(body);
    }
}
