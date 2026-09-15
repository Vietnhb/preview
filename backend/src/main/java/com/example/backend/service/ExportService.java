package com.example.backend.service;

import com.example.backend.dto.physics.SimulationResponse;
import com.example.backend.dto.problem.ProblemResponse;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.entity.Simulation;
import com.example.backend.entity.Specification;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.SimulationRepository;
import com.example.backend.repository.SpecificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

@Service
@RequiredArgsConstructor
public class ExportService {
    private final SpecificationRepository specificationRepository;
    private final SimulationRepository simulationRepository;
    private final CurrentUserService currentUserService;
    private final SimulationService simulationService;
    private final SchemaService schemaService;
    private final ProblemResponseMapper problemMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public byte[] specificationJson(java.util.UUID specificationId) {
        User user = currentUserService.requireCurrentUser();
        Specification specification = specificationRepository.findByIdAndSubmissionOwner(specificationId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specification not found"));
        ObjectNode document = objectMapper.createObjectNode();
        document.set("specification", objectMapper.valueToTree(problemMapper.toSpecification(specification)));
        List<SchemaVersion> schemas = schemaService.list(false);
        schemas.stream().filter(schema -> schema.getSchemaId().equalsIgnoreCase(resolveSchema(specification)))
                .findFirst().ifPresent(schema -> document.set("schema", objectMapper.valueToTree(schema)));
        ObjectNode solver = objectMapper.createObjectNode();
        solver.put("solverId", resolveSchema(specification) + "-solver");
        solver.put("version", "1.0.0");
        document.set("solverMetadata", solver);
        document.set("validationResults", specification.getValidationResult());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not export specification");
        }
    }

    @Transactional(readOnly = true)
    public byte[] simulationCsv(java.util.UUID specificationId) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> item.getSpecification().getId().equals(specificationId))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No simulation run found"));
        SimulationResponse response = simulationService.get(simulation.getId());
        StringBuilder csv = new StringBuilder("time");
        List<String> columns = new ArrayList<>(response.values().keySet());
        for (String column : columns)
            csv.append(',').append(column);
        csv.append('\n');
        for (int i = 0; i < response.time().size(); i++) {
            csv.append(response.time().get(i));
            for (String column : columns) {
                List<Double> values = response.values().get(column);
                csv.append(',').append(values != null && i < values.size() ? values.get(i) : "");
            }
            csv.append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] simulationPdf(java.util.UUID specificationId) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> item.getSpecification().getId().equals(specificationId))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No simulation run found"));
        SimulationResponse response = simulationService.get(simulation.getId());
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                stream.newLineAtOffset(50, 750);
                stream.showText("PhysLive simulation report");
                stream.endText();
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                stream.newLineAtOffset(50, 720);
                stream.showText("Schema: " + response.schemaId());
                stream.newLineAtOffset(0, -16);
                stream.showText("Validation: " + response.validationPassed());
                stream.newLineAtOffset(0, -16);
                stream.showText("Samples: " + response.time().size());
                stream.newLineAtOffset(0, -24);
                int rows = Math.min(response.time().size(), 32);
                List<String> columns = new ArrayList<>(response.values().keySet());
                for (int i = 0; i < rows; i++) {
                    StringBuilder line = new StringBuilder("t=")
                            .append(String.format(java.util.Locale.ROOT, "%.3f", response.time().get(i)));
                    for (String column : columns) {
                        List<Double> values = response.values().get(column);
                        if (values != null && i < values.size())
                            line.append(" ").append(column).append("=")
                                    .append(String.format(java.util.Locale.ROOT, "%.3f", values.get(i)));
                    }
                    stream.showText(line.toString());
                    stream.newLineAtOffset(0, -14);
                }
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not export PDF report");
        }
    }

    @Transactional(readOnly = true)
    public byte[] offlineReplayHtml(java.util.UUID specificationId) {
        User user = currentUserService.requireCurrentUser();
        Specification specification = specificationRepository.findByIdAndSubmissionOwner(specificationId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specification not found"));
        Simulation simulation = simulationRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> item.getSpecification().getId().equals(specificationId))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No simulation run found"));
        if (!"PASSED".equals(specification.getValidationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only validated simulations can be exported for offline replay");
        }
        SimulationResponse response = simulationService.get(simulation.getId());
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.set("specification", objectMapper.valueToTree(problemMapper.toSpecification(specification)));
        bundle.set("simulation", objectMapper.valueToTree(response));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(bundle).replace("</", "<\\/");
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create offline replay");
        }
        String html = """
                <!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>PhysLive Offline Replay</title><style>
                body{font-family:system-ui;margin:0;background:#f4f7fb;color:#10213d}main{max-width:980px;margin:auto;padding:24px}
                header{display:flex;justify-content:space-between;align-items:center}canvas{width:100%%;height:420px;background:white;border:1px solid #cbd7e8;border-radius:18px}
                input{width:100%%}.meta{display:flex;gap:16px;flex-wrap:wrap}.meta span{background:#e5f3ed;padding:8px 12px;border-radius:999px}
                </style></head><body><main><header><div><small>PHYSLIVE OFFLINE REPLAY</small><h1 id="title"></h1></div><strong id="time"></strong></header>
                <canvas id="scene" width="960" height="420"></canvas><input id="timeline" type="range" min="0" value="0" step="1"><p><button id="play">▶ Phát</button></p><div class="meta" id="meta"></div>
                </main><script id="bundle" type="application/json">%s</script><script>
                const b=JSON.parse(document.getElementById('bundle').textContent),s=b.simulation,v=s.visualization||{},times=s.time||[];
                title.textContent=b.specification.schemaId;timeline.max=Math.max(0,times.length-1);meta.innerHTML=`<span>Schema ${b.specification.schemaVersion}</span><span>Validation ${s.validationPassed?'PASSED':'FAILED'}</span><span>${times.length} mẫu</span>`;
                const ctx=scene.getContext('2d');let timer=null;function at(path,i){const [g,k]=path.split('.');return s[g]?.[k]?.[i]??0}function draw(){const i=+timeline.value;ctx.clearRect(0,0,960,420);ctx.strokeStyle='#b8c5d8';ctx.beginPath();ctx.moveTo(50,330);ctx.lineTo(910,330);ctx.stroke();time.textContent=`t = ${(times[i]??0).toFixed(2)} s`;const list=v.series||[];list.forEach((x,n)=>{const vals=s[x.source.split('.')[0]]?.[x.source.split('.')[1]]||[];if(!vals.length)return;const lo=Math.min(...vals),hi=Math.max(...vals),px=60+((vals[i]-lo)/Math.max(hi-lo,1e-9))*840;ctx.fillStyle=x.color||'#2563eb';ctx.beginPath();ctx.arc(px,130+n*52,14,0,Math.PI*2);ctx.fill();ctx.fillText(`${x.label}: ${(vals[i]??0).toFixed(3)} ${x.unit}`,60,115+n*52)});}
                timeline.oninput=draw;play.onclick=()=>{if(timer){clearInterval(timer);timer=null;play.textContent='▶ Phát';return}play.textContent='⏸ Dừng';timer=setInterval(()=>{timeline.value=(+timeline.value+1)%%Math.max(1,times.length);draw()},50)};draw();
                </script></body></html>
                """
                .formatted(payload);
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private String resolveSchema(Specification specification) {
        if (specification.getSchemaId() != null && !specification.getSchemaId().isBlank())
            return specification.getSchemaId();
        throw new ApiException(HttpStatus.CONFLICT, "Specification schemaId is missing");
    }
}
