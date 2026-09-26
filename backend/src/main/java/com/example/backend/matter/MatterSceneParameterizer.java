package com.example.backend.matter;

import java.util.ArrayList;
import java.util.List;

import com.example.backend.matter.MatterFlowResponse.Parameter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Derives local sliders from validated scene fields instead of model-authored names. */
public final class MatterSceneParameterizer {
    private static final int MAX_PARAMETERS = 30;
    private static final double LIMIT = 1_000_000;

    private MatterSceneParameterizer() { }

    public static Prepared prepare(JsonNode source) {
        if (!(source instanceof ObjectNode original)) {
            throw new MatterSceneCompiler.InvalidSceneException("Scene is missing");
        }
        ObjectNode scene = original.deepCopy();
        List<Parameter> parameters = new ArrayList<>();
        ObjectNode gravity = object(scene.path("gravity"));
        if (gravity != null) {
            add(gravity, "y", "gravity_y", "m/s^2", Kind.SIGNED, parameters, true);
            add(gravity, "x", "gravity_x", "m/s^2", Kind.SIGNED, parameters, false);
        }
        if (scene.path("bodies") instanceof ArrayNode bodies) {
            for (int index = 0; index < bodies.size(); index++) {
                ObjectNode body = object(bodies.get(index));
                if (body == null) continue;
                boolean moving = !body.path("isStatic").asBoolean();
                String base = "body" + index + "_";
                if (moving) add(body, "mass", base + "mass", "kg", Kind.POSITIVE, parameters, true);
                add(body, "x", base + "x", "m", Kind.SIGNED, parameters, false);
                add(body, "y", base + "y", "m", Kind.SIGNED, parameters, false);
                if (moving) {
                    add(body, "vx", base + "vx", "m/s", Kind.SIGNED, parameters, true);
                    add(body, "vy", base + "vy", "m/s", Kind.SIGNED, parameters, true);
                }
                if ("circle".equals(body.path("shape").asText())) {
                    add(body, "radius", base + "radius", "m", Kind.POSITIVE, parameters, true);
                } else if ("rectangle".equals(body.path("shape").asText())) {
                    add(body, "width", base + "width", "m", Kind.POSITIVE, parameters, true);
                    add(body, "height", base + "height", "m", Kind.POSITIVE, parameters, true);
                }
                add(body, "restitution", base + "restitution", "", Kind.UNIT_INTERVAL,
                        parameters, moving);
                add(body, "friction", base + "friction", "", Kind.UNIT_INTERVAL,
                        parameters, false);
                if (moving) add(body, "frictionAir", base + "frictionAir", "",
                        Kind.UNIT_INTERVAL, parameters, false);
            }
        }
        if (scene.path("constraints") instanceof ArrayNode constraints) {
            for (int index = 0; index < constraints.size(); index++) {
                ObjectNode link = object(constraints.get(index));
                if (link == null) continue;
                String base = "link" + index + "_";
                add(link, "length", base + "length", "m", Kind.NONNEGATIVE, parameters, true);
                add(link, "stiffness", base + "stiffness", "", Kind.UNIT_INTERVAL, parameters, true);
                add(link, "damping", base + "damping", "", Kind.UNIT_INTERVAL, parameters, false);
            }
        }
        return new Prepared(scene, List.copyOf(parameters));
    }

    private static ObjectNode object(JsonNode node) { return node instanceof ObjectNode value ? value : null; }

    private static void add(ObjectNode object, String field, String name, String unit, Kind kind,
            List<Parameter> parameters, boolean includeZero) {
        if (parameters.size() >= MAX_PARAMETERS || !name.matches("[A-Za-z_]\\w{0,50}")) return;
        JsonNode node = object.path(field);
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())) return;
        double value = node.doubleValue();
        if (Math.abs(value) > LIMIT || value == 0 && !includeZero) return;
        double span = Math.max(1, Math.abs(value) * 2);
        double min;
        double max;
        switch (kind) {
            case SIGNED -> { min = Math.max(-LIMIT, value - span); max = Math.min(LIMIT, value + span); }
            case POSITIVE -> { min = Math.max(0.000001, value / 10); max = Math.min(LIMIT, value * 5); }
            case NONNEGATIVE -> { min = 0; max = Math.clamp(value * 5, 1, LIMIT); }
            case UNIT_INTERVAL -> { min = 0; max = 1; }
            default -> throw new IllegalStateException("Unknown parameter kind");
        }
        if (min > value || max < value || min == max) return;
        String owner = object.path("label").asText(object.path("id").asText("Gravity"));
        String id = object.path("id").asText("");
        if (!id.isBlank() && !id.equals(owner)) owner += " [" + id + "]";
        parameters.add(new Parameter(name, value, unit, min, max, owner + " · " + field));
        object.put(field, name);
    }

    private enum Kind { SIGNED, POSITIVE, NONNEGATIVE, UNIT_INTERVAL }
    public record Prepared(JsonNode scene, List<Parameter> parameters) { }
}
