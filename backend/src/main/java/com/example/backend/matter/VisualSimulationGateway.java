package com.example.backend.matter;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.ai.extraction.validation.ResponseSchemaValidator;
import com.example.backend.config.properties.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Generates a self-contained model and drawing program for non-rigid-body phenomena. */
@Component
public final class VisualSimulationGateway {
    private static final Logger log = LoggerFactory.getLogger(VisualSimulationGateway.class);
    private static final int MAX_PROGRAM_PART_LENGTH = 50_000;
    private static final String PROMPT = """
            Generate an interactive physics simulation of the
            confirmed Physics grade 10, 11 or 12 description. The simulation must look
            professional, educational, and visually stunning — like a modern interactive
            textbook illustration. Derive behavior from the user's stated laws, quantities,
            objects and assumptions and generally accepted high-school physics. Preserve all
            user-stated values and distinct objects. Use the disclosed defaults only where
            the user did not give a value. If the physics cannot be determined honestly,
            return UNSUPPORTED in the user's language; do not invent a different phenomenon.

            Return one JSON object matching the schema. For READY, program.init,
            program.step and program.draw are JavaScript function bodies, not whole
            functions. init(params,width,height) returns a JSON-serializable state object;
            step(state,dt,params,width,height) mutates it for elapsed seconds dt;
            draw(state,paint,params,width,height) draws one frame in viewport pixels.
            The first draw runs immediately after init, before any step. Initialize
            every state field read by draw to its finite physical starting value.
            The runtime calls step and draw locally; changing a parameter reruns init.
            The runtime sets state.pointer to {x,y,down} before the first draw and
            updates it for pointer input; use it only when direct interaction fits the
            confirmed phenomenon.
            Every body uses local const/let declarations, finite arithmetic, ordinary
            object/array values, local helper functions and template strings. For loops
            use finite numeric bounds or a local/state array length. Owned
            arrays support push, pop, shift, unshift and slice. All loops and helper
            calls share a budget of 2000 operations per phase; call depth is at most
            128 and arrays/indices remain within 2000 entries. No imports, new, global
            objects, recursion, timers,
            network, DOM, eval, dynamic property names, or asynchronous work.
            Available math: Math.PI, Math.E and abs, min, max, sqrt, pow, sin, cos, tan,
            atan2, asin, acos, exp, log, log10, log2, cbrt, sign, trunc, expm1, log1p,
            random, floor, ceil, round, hypot. Numeric labels may use toFixed or
            toPrecision with a literal precision of at most 20. State and parameter
            fields use dot notation or an index from a bounded for loop.

            DRAWING & SVG RENDERING API — use these to create stunning, vector-sharp visuals:
            • paint.defs(svgDefsXml) — define reusable SVG filters (e.g. feDropShadow, feGaussianBlur)
              and gradients (<linearGradient>, <radialGradient>). Call once or each draw.
            • paint.svg(svgXml, x, y) — render rich procedural SVG vector markup at (x, y). Supports
              <g>, <path>, <rect>, <circle>, <polygon>, filters, gradients, transforms!
            • paint.svgPath(d, fill, stroke, strokeWidth, shadowColor, shadowBlur, shadowOffsetY) —
              draw SVG path directly on canvas with optional drop shadow.
            • paint.circle(x,y,r,color) — filled circle
            • paint.strokeCircle(x,y,r,color,strokeWidth) — circle outline
            • paint.rect(x,y,w,h,color) — filled rectangle
            • paint.strokeRect(x,y,w,h,color,strokeWidth) — rectangle outline
            • paint.roundRect(x,y,w,h,radius,color) — rounded rectangle
            • paint.line(x1,y1,x2,y2,color,strokeWidth) — solid line
            • paint.dashedLine(x1,y1,x2,y2,color,strokeWidth) — dashed line
            • paint.arrow(x1,y1,x2,y2,color,strokeWidth) — line with arrowhead
            • paint.arc(x,y,r,startAngle,endAngle,color,strokeWidth) — arc stroke
            • paint.gradientRect(x,y,w,h,color1,color2,vertical) — gradient fill
            • paint.gradientCircle(x,y,r,colorCenter,colorEdge) — radial gradient
            • paint.polygon(flatPointsArray,color) — filled polygon [x0,y0,x1,y1,...]
            • paint.text(x,y,text,color,fontSize) — centered text label
            • paint.background(color) — set a scene-appropriate background for the frame
            Colors are CSS hex strings (#rrggbb or #rrggbbaa for transparency).

            RENDERING CONTRACT — these are mandatory:
            Derive the scene graph, appearance, layout, colors, annotations and optional
            plots from confirmedDescription and simulationSpec. Do not select an asset ID,
            assume a particular object kind, copy a stock scene, or force a fixed palette
            or panel layout. Represent every requiredObjects entry and its full count;
            keep a stable state record for every independently moving participant and
            iterate the complete collection in step and draw. Draw all stated connections,
            media, boundaries and interactions. Use procedural primitives or SVG markup
            composed specifically for each named participant. Geometry and transforms must
            be driven by state and parameters. Add vectors, traces, graphs and annotations
            only when they clarify quantities present in the confirmed model. The result
            must remain readable, unclipped and distinguishable for any supported number
            of participants within the runtime budgets.
            Call paint.background in draw and derive its color from the described scene;
            the runtime does not impose a themed grid, asset backdrop or stock environment.

            Keep state under 256 KB and at most 1000 draw commands per frame. Place
            relevant objects and labels visibly in the viewport. Do not require a
            pre-saved asset. The canvas background is dark #0b1729; use contrasting
            colors, readable labels and at least 48 pixels of outer margin. Lay out
            requested objects, physical connections and graphs in separate visible
            regions without clipping. Draw and name every participant, not only the
            changing scalar. When plotting a time-dependent quantity, draw the curve
            over the displayed time range with quantitative axes and a moving marker.
            Parameters are finite numeric controls with unique JS-safe
            names and min <= value <= max; omit controls that would change the kind of
            phenomenon. params contains the numeric controls you return, with no
            implicit timing fields. Store any other needed values in init's state.
            Preserve other stated values directly in the model even when
            they are not controls. durationSeconds is the presentation run time, from
            0.1 to 40; an explicitly requested physical time span may be mapped to that
            interval in the model, with the physical clock labeled correctly. Never
            replace a user-given physical duration with the presentation duration.
            Include at most 30 controls, without truncating objects or fixed values.
            Before returning READY, check the JavaScript syntax and trace init plus
            the first draw and step with the declared values. Every referenced state
            field must exist; all resulting drawing coordinates must remain finite.
            """;

    private final ChatCompletionClient client;
    private final AiProviderProperties properties;
    private final ObjectMapper mapper;
    private final JsonNode schema;

    public VisualSimulationGateway(ChatCompletionClient client, AiProviderProperties properties,
            ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
        try (var input = VisualSimulationGateway.class.getResourceAsStream(
                "/prompts/visual-simulation-response-schema.json")) {
            if (input == null) throw new IllegalStateException("Visual response schema is missing");
            schema = mapper.readTree(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read visual response schema", failure);
        }
    }

    public JsonNode plan(String description, String explanation, JsonNode specification) {
        var context = mapper.createObjectNode();
        context.put("confirmedDescription", description);
        context.put("confirmedExplanation", explanation);
        context.set("simulationSpec", specification);
        context.set("responseContract", schema);
        JsonNode result = client.parseJson(client.completeJsonReasoned(properties.textModel(), List.of(
                client.textMessage("system", PROMPT),
                client.textMessage("user", context.toString()))).content());
        if (Boolean.getBoolean("physlive.matter-live-e2e")) {
            log.debug("VISUAL_LIVE_PLAN={}", result);
        }
        ResponseSchemaValidator.validate(result, schema);
        if ("UNSUPPORTED".equals(result.path("status").asText())) return result;
        VisualProgramPackaging.normalize(result.path("program"));
        if (!"READY".equals(result.path("status").asText())) {
            throw new InvalidVisualProgramException("Visual planner returned an invalid status");
        }
        JsonNode program = result.path("program");
        if (!program.isObject()) throw new InvalidVisualProgramException("Visual program is missing");
        for (String phase : List.of("init", "step", "draw")) {
            JsonNode part = program.path(phase);
            if (!part.isTextual() || part.asText().length() > MAX_PROGRAM_PART_LENGTH) {
                throw new InvalidVisualProgramException("Visual " + phase + " code is too large");
            }
            // allow empty step (some visual models don't need stepping)
            if (!"step".equals(phase) && part.asText().isBlank()) {
                throw new InvalidVisualProgramException("Visual " + phase + " code is empty");
            }
        }
        JsonNode duration = result.path("durationSeconds");
        if (!duration.isNumber() || !Double.isFinite(duration.doubleValue())
                || duration.doubleValue() < 0.1 || duration.doubleValue() > 40) {
            throw new InvalidVisualProgramException(
                    "Visual duration must be between 0.1 and 40 seconds");
        }
        JsonNode declared = result.path("parameters");
        if (!declared.isArray() || declared.size() > 30) {
            throw new InvalidVisualProgramException(
                    "Visual program must declare an array of at most 30 controls");
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode parameter : declared) {
            String name = parameter.path("name").asText("");
            if (!name.matches("[A-Za-z_]\\w{0,50}") || !names.add(name)) {
                throw new InvalidVisualProgramException(
                        "Visual controls need unique JavaScript-safe names");
            }
            double value = parameter.path("value").asDouble(Double.NaN);
            double min = parameter.path("min").asDouble(Double.NaN);
            double max = parameter.path("max").asDouble(Double.NaN);
            if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max)) {
                throw new InvalidVisualProgramException("Visual control values must be finite");
            }
            if (min > value || value > max || min < -1e30 || max > 1e30) {
                throw new InvalidVisualProgramException(
                        "Visual control range does not contain its value");
            }
        }
        return result;
    }

    public static final class InvalidVisualProgramException extends RuntimeException {
        public InvalidVisualProgramException(String message) { super(message); }
    }
}
