package com.example.backend.matter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.backend.matter.MatterFlowResponse.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Compiles validated, SI-unit scene data into a small fixed Matter.js program. */
public final class MatterSceneCompiler {
    static final int MAX_BODIES = 60;
    static final int MAX_CONSTRAINTS = 80;
    static final double MAX_ABSOLUTE = 1_000_000;
    private static final String GRAVITY = "gravity";
    private static final String EXPECTED_CONTACTS = "expectedContacts";
    private static final String CIRCLE = "circle";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String IS_STATIC = "isStatic";
    private static final String CENTER_X = "centerX";
    private static final String CENTER_Y = "centerY";
    private static final String SCALE_SUFFIX = ") * scale";
    private static final String CONST_DECLARATION = "const ";

    private final ObjectMapper mapper;
    private final Set<String> usedParameters = new HashSet<>();
    private final List<ExpectedContact> expectedContacts = new ArrayList<>();
    private double durationSeconds;

    public MatterSceneCompiler(ObjectMapper mapper) { this.mapper = mapper; }

    public Set<String> usedParameters() { return Set.copyOf(usedParameters); }
    public List<ExpectedContact> expectedContacts() { return List.copyOf(expectedContacts); }
    public double durationSeconds() { return durationSeconds; }

    public String compile(JsonNode scene, List<Parameter> parameters) {
        usedParameters.clear();
        expectedContacts.clear();
        if (!scene.isObject() || !"SI".equals(scene.path("units").asText())) {
            throw invalid("Scene must use SI units");
        }
        durationSeconds = literal(scene.path("durationSeconds"), "durationSeconds");
        if (durationSeconds < 0.1 || durationSeconds > 40) {
            throw invalid("Scene duration must be between 0.1 and 40 seconds");
        }
        if (!scene.path("view").isObject() || !scene.path(GRAVITY).isObject()
                || !scene.path("bodies").isArray() || !scene.path("constraints").isArray()
                || !scene.path(EXPECTED_CONTACTS).isArray()) {
            throw invalid("Scene structure is incomplete");
        }
        var available = new HashMap<String, Parameter>();
        for (Parameter parameter : parameters) available.put(parameter.name(), parameter);
        JsonNode view = scene.path("view");
        Scalar minX = scalar(view.path("minX"), "m", available);
        Scalar maxX = scalar(view.path("maxX"), "m", available);
        Scalar minY = scalar(view.path("minY"), "m", available);
        Scalar maxY = scalar(view.path("maxY"), "m", available);
        if (minX.max >= maxX.min || minY.max >= maxY.min) {
            throw invalid("Scene view bounds can cross within parameter range");
        }

        JsonNode bodies = scene.path("bodies");
        JsonNode constraints = scene.path("constraints");
        if (bodies.isEmpty() || bodies.size() > MAX_BODIES || constraints.size() > MAX_CONSTRAINTS) {
            throw invalid("Scene exceeds supported body or constraint count");
        }
        List<Body> parsedBodies = new ArrayList<>();
        Map<String, Integer> bodyIds = new HashMap<>();
        for (JsonNode body : bodies) {
            if (!body.isObject()) throw invalid("Scene body is invalid");
            String id = body.path("id").asText("");
            String label = body.path("label").asText("");
            String shape = body.path("shape").asText("");
            if (!id.matches("[A-Za-z_]\\w{0,30}") || bodyIds.putIfAbsent(id, parsedBodies.size()) != null
                    || label.isBlank() || label.length() > 60
                    || (!shape.equals(CIRCLE) && !shape.equals("rectangle"))) {
                throw invalid("Scene body identifier, label, or shape is invalid");
            }
            Scalar x = scalar(body.path("x"), "m", available);
            Scalar y = scalar(body.path("y"), "m", available);
            Scalar radius = scalar(body.path("radius"), "m", available);
            Scalar width = scalar(body.path(WIDTH), "m", available);
            Scalar height = scalar(body.path(HEIGHT), "m", available);
            Scalar mass = scalar(body.path("mass"), "kg", available);
            Scalar vx = scalar(body.path("vx"), "m/s", available);
            Scalar vy = scalar(body.path("vy"), "m/s", available);
            Scalar restitution = scalar(body.path("restitution"), "", available);
            Scalar friction = scalar(body.path("friction"), "", available);
            Scalar frictionAir = scalar(body.path("frictionAir"), "", available);
            if (!body.path(IS_STATIC).isBoolean()
                    || !body.path(IS_STATIC).asBoolean() && mass.min <= 0
                    || restitution.min < 0 || restitution.max > 1
                    || friction.min < 0 || friction.max > 1
                    || frictionAir.min < 0 || frictionAir.max > 1) {
                throw invalid("Scene body has invalid physical properties");
            }
            // Inactive dimensions are metadata from the scene planner. Only the
            // dimension used by the selected shape affects the Matter body.
            if (shape.equals(CIRCLE) ? radius.min <= 0
                    : width.min <= 0 || height.min <= 0) {
                throw invalid("Scene body dimensions do not match its shape");
            }
            boolean isStatic = body.path(IS_STATIC).booleanValue();
            if (isStatic && (vx.baseline != 0 || vy.baseline != 0)) {
                throw invalid("A static body cannot have initial velocity");
            }
            if (isStatic && body.path("mass").isTextual()) {
                throw invalid("A static body's mass cannot be an adjustable parameter");
            }
            Body parsed = new Body(id, label, shape, x, y, radius, width, height, mass,
                    vx, vy, isStatic, restitution, friction, frictionAir);
            parsedBodies.add(parsed);
        }

        List<Constraint> parsedConstraints = new ArrayList<>();
        for (JsonNode item : constraints) {
            if (!item.isObject()) throw invalid("Scene constraint is invalid");
            Integer a = endpoint(item.path("bodyA"), bodyIds);
            Integer b = endpoint(item.path("bodyB"), bodyIds);
            if (a == null && b == null) throw invalid("A constraint needs a body endpoint");
            Scalar ax = scalar(item.path("anchorAx"), "m", available);
            Scalar ay = scalar(item.path("anchorAy"), "m", available);
            Scalar bx = scalar(item.path("anchorBx"), "m", available);
            Scalar by = scalar(item.path("anchorBy"), "m", available);
            Scalar length = scalar(item.path("length"), "m", available);
            Scalar stiffness = scalar(item.path("stiffness"), "", available);
            Scalar damping = scalar(item.path("damping"), "", available);
            if (length.min < 0 || stiffness.min < 0 || stiffness.max > 1
                    || damping.min < 0 || damping.max > 1) {
                throw invalid("Scene constraint has invalid physical properties");
            }
            parsedConstraints.add(new Constraint(a, b, ax, ay, bx, by, length, stiffness, damping));
        }
        if (scene.path(EXPECTED_CONTACTS).size() > MAX_CONSTRAINTS) {
            throw invalid("Scene has too many expected contacts");
        }
        for (JsonNode contact : scene.path(EXPECTED_CONTACTS)) {
            String a = contact.path("bodyA").asText("");
            String b = contact.path("bodyB").asText("");
            if (!bodyIds.containsKey(a) || !bodyIds.containsKey(b) || a.equals(b)) {
                throw invalid("Expected contact references invalid body IDs");
            }
            expectedContacts.add(new ExpectedContact(a, b));
        }

        Scalar gx = scalar(scene.path(GRAVITY).path("x"), "m/s^2", available);
        Scalar gy = scalar(scene.path(GRAVITY).path("y"), "m/s^2", available);
        if (maxX.max - minX.min > MAX_ABSOLUTE || maxY.max - minY.min > MAX_ABSOLUTE) {
            throw invalid("Scene view is too large for the renderer");
        }
        StringBuilder code = new StringBuilder();
        code.append("const engine = Matter.Engine.create();\n");
        List<String> left = new ArrayList<>(List.of(minX.js));
        List<String> right = new ArrayList<>(List.of(maxX.js));
        List<String> top = new ArrayList<>(List.of(minY.js));
        List<String> bottom = new ArrayList<>(List.of(maxY.js));
        for (Body body : parsedBodies) {
            String hx = body.shape.equals(CIRCLE) ? body.radius.js : "(" + body.width.js + ") / 2";
            String hy = body.shape.equals(CIRCLE) ? body.radius.js : "(" + body.height.js + ") / 2";
            left.add("(" + body.x.js + ") - (" + hx + ")");
            right.add("(" + body.x.js + ") + (" + hx + ")");
            top.add("(" + body.y.js + ") - (" + hy + ")");
            bottom.add("(" + body.y.js + ") + (" + hy + ")");
        }
        for (Constraint c : parsedConstraints) {
            if (c.a == null) { left.add(c.ax.js); right.add(c.ax.js);
                top.add(c.ay.js); bottom.add(c.ay.js); }
            if (c.b == null) { left.add(c.bx.js); right.add(c.bx.js);
                top.add(c.by.js); bottom.add(c.by.js); }
        }
        code.append("const left = Math.min(").append(String.join(", ", left)).append(");\n");
        code.append("const right = Math.max(").append(String.join(", ", right)).append(");\n");
        code.append("const top = Math.min(").append(String.join(", ", top)).append(");\n");
        code.append("const bottom = Math.max(").append(String.join(", ", bottom)).append(");\n");
        code.append("const scale = Math.min((width - 96) / Math.max(0.1, right - left), ")
                .append("(height - 96) / Math.max(0.1, bottom - top));\n");
        code.append(CONST_DECLARATION).append(CENTER_X).append(" = (left + right) / 2;\n");
        code.append(CONST_DECLARATION).append(CENTER_Y).append(" = (top + bottom) / 2;\n");
        code.append("engine.gravity.x = ").append(gx.js).append(";\n");
        code.append("engine.gravity.y = ").append(gy.js).append(";\n");
        code.append("engine.gravity.scale = scale / 1000000;\n");
        for (int i = 0; i < parsedBodies.size(); i++) {
            Body body = parsedBodies.get(i);
            String local = "body" + i;
            String x = screen(body.x.js, CENTER_X, WIDTH);
            String y = screen(body.y.js, CENTER_Y, HEIGHT);
            code.append(CONST_DECLARATION).append(local).append(" = Matter.Bodies.").append(body.shape).append('(')
                    .append(x).append(", ").append(y).append(", ");
            if (body.shape.equals(CIRCLE)) code.append('(').append(body.radius.js).append(SCALE_SUFFIX);
            else code.append('(').append(body.width.js).append(SCALE_SUFFIX).append(", (")
                    .append(body.height.js).append(SCALE_SUFFIX);
            code.append(", { isStatic: ").append(body.isStatic)
                    .append(", restitution: ").append(body.restitution.js)
                    .append(", friction: ").append(body.friction.js)
                    .append(", frictionAir: ").append(body.frictionAir.js)
                    .append(", label: ").append(quoted(body.label))
                    .append(", plugin: { physliveId: ").append(quoted(body.id))
                    .append(" } });\n");
            if (!body.isStatic) {
                code.append("Matter.Body.setMass(").append(local).append(", ")
                        .append(body.mass.js).append(");\n");
                code.append("Matter.Body.setVelocity(").append(local).append(", { x: (")
                        .append(body.vx.js).append(") * scale / 60, y: (")
                        .append(body.vy.js).append(") * scale / 60 });\n");
            }
        }
        for (int i = 0; i < parsedConstraints.size(); i++) {
            Constraint c = parsedConstraints.get(i);
            code.append("const link").append(i).append(" = Matter.Constraint.create({ bodyA: ")
                    .append(c.a == null ? "null" : "body" + c.a)
                    .append(", bodyB: ").append(c.b == null ? "null" : "body" + c.b)
                    .append(", pointA: { x: ").append(c.a == null ? screen(c.ax.js, CENTER_X, WIDTH)
                            : "(" + c.ax.js + ") * scale")
                    .append(", y: ").append(c.a == null ? screen(c.ay.js, CENTER_Y, HEIGHT)
                            : "(" + c.ay.js + ") * scale")
                    .append(" }, pointB: { x: ").append(c.b == null ? screen(c.bx.js, CENTER_X, WIDTH)
                            : "(" + c.bx.js + ") * scale")
                    .append(", y: ").append(c.b == null ? screen(c.by.js, CENTER_Y, HEIGHT)
                            : "(" + c.by.js + ") * scale")
                    .append(" }, length: (").append(c.length.js).append(") * scale")
                    .append(", stiffness: ").append(c.stiffness.js)
                    .append(", damping: ").append(c.damping.js).append(" });\n");
        }
        code.append("Matter.Composite.add(engine.world, [");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < parsedBodies.size(); i++) names.add("body" + i);
        for (int i = 0; i < parsedConstraints.size(); i++) names.add("link" + i);
        code.append(String.join(", ", names)).append("]);\nreturn engine;");
        String program = code.toString();
        MatterCodeSafety.verify(program, available.keySet());
        return program;
    }

    private Integer endpoint(JsonNode node, Map<String, Integer> ids) {
        if (node.isNull()) return null;
        if (!node.isTextual() || !ids.containsKey(node.asText())) throw invalid("Unknown constraint body");
        return ids.get(node.asText());
    }

    private Scalar scalar(JsonNode node, String expectedUnit, Map<String, Parameter> parameters) {
        if (node.isNumber()) {
            double value = literal(node, "scene value");
            return new Scalar(number(value), value, value, value);
        }
        if (!node.isTextual() || !parameters.containsKey(node.asText())) {
            throw invalid("Scene references an undeclared parameter or nonnumeric value");
        }
        Parameter parameter = parameters.get(node.asText());
        String unit = parameter.unit().replace("²", "^2").trim();
        if (!unit.equals(expectedUnit) && !(expectedUnit.isEmpty() && unit.equals("1"))) {
            throw invalid("Parameter unit does not match scene field: " + parameter.name());
        }
        usedParameters.add(parameter.name());
        return new Scalar("params." + parameter.name(), parameter.value(), parameter.min(), parameter.max());
    }

    private double literal(JsonNode node, String field) {
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())
                || Math.abs(node.doubleValue()) > MAX_ABSOLUTE) throw invalid("Invalid number in " + field);
        return node.doubleValue();
    }

    private String screen(String value, String centre, String size) {
        return "(" + size + " / 2 + ((" + value + ") - " + centre + ") * scale)";
    }

    private String quoted(String value) {
        try { return mapper.writeValueAsString(value).replace("<", "\\x3c"); }
        catch (JsonProcessingException failure) { throw invalid("Cannot encode body label"); }
    }

    private String number(double value) { return Double.toString(value == 0 ? 0 : value); }

    private InvalidSceneException invalid(String message) { return new InvalidSceneException(message); }

    private record Scalar(String js, double baseline, double min, double max) { }
    private record Body(String id, String label, String shape, Scalar x, Scalar y, Scalar radius,
            Scalar width, Scalar height, Scalar mass, Scalar vx, Scalar vy, boolean isStatic,
            Scalar restitution, Scalar friction, Scalar frictionAir) { }
    private record Constraint(Integer a, Integer b, Scalar ax, Scalar ay, Scalar bx, Scalar by,
            Scalar length, Scalar stiffness, Scalar damping) { }

    public record ExpectedContact(String bodyA, String bodyB) { }

    public static final class InvalidSceneException extends RuntimeException {
        public InvalidSceneException(String message) { super(message); }
    }
}
