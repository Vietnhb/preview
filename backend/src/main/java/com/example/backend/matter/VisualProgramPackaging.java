package com.example.backend.matter;

import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Accepts equivalent function packaging; the frontend validates executable contents. */
final class VisualProgramPackaging {
    private static final Map<String, String> SIGNATURES = Map.of(
            "init", "params,width,height", "step", "state,dt,params,width,height",
            "draw", "state,paint,params,width,height");

    private VisualProgramPackaging() { }

    static void normalize(JsonNode program) {
        if (!(program instanceof ObjectNode mutable)) return;
        SIGNATURES.forEach((phase, signature) -> {
            JsonNode value = mutable.path(phase);
            if (!value.isTextual()) return;
            String code = value.asText().trim();
            String parameters = signature.replace(",", "\\s*,\\s*");
            Pattern wrapper = Pattern.compile("^(?:(?:function\\s*(?:" + phase + ")?|" + phase
                    + ")\\s*\\(\\s*" + parameters + "\\s*\\)|\\(\\s*" + parameters
                    + "\\s*\\)\\s*=>)\\s*\\{([\\s\\S]*)\\}\\s*;?$");
            var match = wrapper.matcher(code);
            if (match.matches()) mutable.put(phase, match.group(1));
        });
    }
}
