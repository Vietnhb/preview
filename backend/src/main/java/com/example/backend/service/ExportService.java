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
    private static final String SPECIFICATION_NOT_FOUND = "Specification not found";
    private static final String SPECIFICATION = "specification";
    private static final String SIMULATION = "simulation";
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SPECIFICATION_NOT_FOUND));
        Simulation simulation = findSimulation(specificationId, user);
        ObjectNode document = objectMapper.createObjectNode();
        document.set(SPECIFICATION, objectMapper.valueToTree(problemMapper.toSpecification(specification)));
        List<SchemaVersion> schemas = schemaService.list(false);
        schemas.stream().filter(schema -> schema.getSchemaId().equalsIgnoreCase(resolveSchema(specification))
                        && schema.getVersion().equals(specification.getSchemaVersion()))
                .findFirst().ifPresent(schema -> document.set("schema", objectMapper.valueToTree(schema)));
        ObjectNode solver = objectMapper.createObjectNode();
        if (simulation == null || simulation.getSolverVersion() == null) {
            solver.putNull("solverId");
            solver.putNull("version");
        } else {
            String[] binding = simulation.getSolverVersion().split(":", 2);
            solver.set("version", objectMapper.getNodeFactory().textNode(binding[0]));
            solver.set("solverId", objectMapper.getNodeFactory().textNode(
                    binding.length > 1 ? binding[1] : simulation.getSolverVersion()));
        }
        document.set("solverMetadata", solver);
        document.set(SIMULATION, simulation == null ? objectMapper.nullNode() : objectMapper.valueToTree(simulationService.get(simulation.getId())));
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
        Simulation simulation = findSimulation(specificationId, user);
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
        Simulation simulation = findSimulation(specificationId, user);
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SPECIFICATION_NOT_FOUND));
        Simulation simulation = simulationRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> item.getSpecification().getId().equals(specificationId))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No simulation run found"));
        if (!"PASSED".equals(specification.getValidationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only validated simulations can be exported for offline replay");
        }
        SimulationResponse response = simulationService.get(simulation.getId());
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.set(SPECIFICATION, objectMapper.valueToTree(problemMapper.toSpecification(specification)));
        bundle.set(SIMULATION, objectMapper.valueToTree(response));
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

    @Transactional(readOnly = true)
    public byte[] slidesHtml(java.util.UUID specificationId) {
        User user = currentUserService.requireCurrentUser();
        Specification specification = specificationRepository.findByIdAndSubmissionOwner(specificationId, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SPECIFICATION_NOT_FOUND));
        if (!"PASSED".equals(specification.getValidationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only validated simulations can be exported as slides");
        }
        Simulation simulation = findSimulation(specificationId, user);
        SimulationResponse response = simulationService.get(simulation.getId());
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.set(SPECIFICATION, objectMapper.valueToTree(problemMapper.toSpecification(specification)));
        bundle.set(SIMULATION, objectMapper.valueToTree(response));
        try {
            String payload = objectMapper.writeValueAsString(bundle).replace("</", "<\\/");
            String html = """
                    <!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                    <title>PhysLive - Slides</title><style>
                    *{box-sizing:border-box}body{margin:0;background:#eef4fb;color:#15233b;font-family:Inter,system-ui,sans-serif}.deck{width:min(1100px,100%%);margin:auto;padding:28px}.slide{min-height:620px;margin:0 0 22px;padding:54px;border:1px solid #d8e2ef;border-radius:24px;background:#fff;box-shadow:0 8px 28px #233d5c14;page-break-after:always}.kicker{color:#3569b8;font-size:12px;font-weight:800;letter-spacing:.12em}.title{font-size:46px;line-height:1.08;margin:18px 0 14px}.muted{color:#667892;line-height:1.7}.metric-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:36px}.metric{padding:20px;border-radius:16px;background:#f1f6fd}.metric strong{display:block;margin-top:8px;font-size:28px;color:#2563eb}.table{width:100%%;border-collapse:collapse;margin-top:26px}.table th,.table td{text-align:left;padding:13px 10px;border-bottom:1px solid #e1e8f0}.table th{color:#5c7190;font-size:12px}.result{padding:18px;border-radius:16px;background:#f5f8fc;margin-top:14px}.controls{display:flex;gap:10px;flex-wrap:wrap}.controls span{padding:8px 12px;border-radius:999px;background:#e8f1fd;color:#244e88}@media print{body{background:#fff}.deck{padding:0}.slide{border:0;box-shadow:none;margin:0;border-radius:0}}
                    </style></head><body><main class="deck" id="deck"></main><script id="bundle" type="application/json">%s</script><script>
                    const b=JSON.parse(document.getElementById('bundle').textContent),s=b.simulation,q=b.specification,deck=document.getElementById('deck');const params=Object.entries(s.parameters||{}).map(([k,v])=>`<span>${k}: ${v}</span>`).join('');const quantities=(q.quantities||[]).map(x=>`<tr><td>${x.name}</td><td>${x.normalizedValue}</td><td>${x.normalizedUnit}</td></tr>`).join('');const values=Object.entries(s.values||{}).map(([k,v])=>`<div class="result"><strong>${k}</strong><br>Điểm đầu: ${v[0]??'—'} · Điểm cuối: ${v[v.length-1]??'—'} · ${v.length} mẫu</div>`).join('');deck.innerHTML=`<section class="slide"><span class="kicker">PHYSLIVE / SIMULATION SLIDES</span><h1 class="title">${q.schemaId||'Mô phỏng vật lý'}</h1><p class="muted">Bộ slide được tạo từ specification đã kiểm chứng độc lập.</p><div class="metric-grid"><div class="metric">Validation<strong>${s.validationPassed?'PASS':'FAIL'}</strong></div><div class="metric">Schema<strong>${q.schemaVersion||'—'}</strong></div><div class="metric">Samples<strong>${(s.time||[]).length}</strong></div></div></section><section class="slide"><span class="kicker">01 / INPUT</span><h2>Các đại lượng đầu vào</h2><table class="table"><thead><tr><th>Tên</th><th>Giá trị chuẩn hóa</th><th>Đơn vị</th></tr></thead><tbody>${quantities}</tbody></table><div class="controls">${params}</div></section><section class="slide"><span class="kicker">02 / OUTPUT</span><h2>Kết quả mô phỏng</h2><p class="muted">Thời gian: ${(s.time||[])[0]??0} → ${(s.time||[]).slice(-1)[0]??0} s</p>${values}</section>`;
                    </script></body></html>
                    """.formatted(payload);
            return html.getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create slides export");
        }
    }

    private Simulation findSimulation(java.util.UUID specificationId, User user) {
        return simulationRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> item.getSpecification().getId().equals(specificationId))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No simulation run found"));
    }

    private String resolveSchema(Specification specification) {
        if (specification.getSchemaId() != null && !specification.getSchemaId().isBlank())
            return specification.getSchemaId();
        throw new ApiException(HttpStatus.CONFLICT, "Specification schemaId is missing");
    }
}
