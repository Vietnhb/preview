package com.example.backend.system.physics.service;

import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Interprets bounded arithmetic ASTs from approved schemas; no scene or topic-specific branches.
 *
 * All participants of a plan advance together in one integration, so an input written
 * "&lt;participantId&gt;.&lt;outputKey&gt;" reads that participant's result at the current instant. A participant may
 * end when a watched result crosses a value ("until") and another may start at that moment ("after"), which is
 * how a description with several stages or a stopping condition is computed from the same approved laws.
 */
@Service
public class SchemaEquationRuntime {
    private final ObjectMapper json;
    private final double defaultStep;
    private final double maxDuration;
    private final int maxSamples;
    private final int maxParticipants;
    private final int maxDepth;
    private final int maxNodes;
    private final Map<String, Map<String, Double>> unitDimensions = new LinkedHashMap<>();
    /** Every spelling of a unit → its canonical spelling and the factor that converts a value to it. */
    private final Map<String, String> canonicalUnit = new LinkedHashMap<>();
    private final Map<String, Double> unitFactor = new LinkedHashMap<>();

    public SchemaEquationRuntime(ObjectMapper json,
            @Value("${physlive.simulation.runtime.default-step-seconds}") double defaultStep,
            @Value("${physlive.simulation.runtime.max-duration-seconds}") double maxDuration,
            @Value("${physlive.simulation.runtime.max-samples}") int maxSamples,
            @Value("${physlive.simulation.runtime.max-participants}") int maxParticipants,
            @Value("${physlive.simulation.runtime.max-equation-depth}") int maxDepth,
            @Value("${physlive.simulation.runtime.max-equation-nodes}") int maxNodes) {
        this.json = json;
        this.defaultStep = defaultStep;
        this.maxDuration = maxDuration;
        this.maxSamples = maxSamples;
        this.maxParticipants = maxParticipants;
        this.maxDepth = maxDepth;
        this.maxNodes = maxNodes;
        try (var input = new ClassPathResource("units/catalog.json").getInputStream()) {
            for (JsonNode unit : json.readTree(input)) {
                Map<String, Double> dimension = new LinkedHashMap<>();
                unit.path("dimensions").fields().forEachRemaining(field -> dimension.put(field.getKey(), field.getValue().asDouble()));
                String canonical = unit.path("canonical").asText();
                double factor = unit.path("factor").asDouble();
                unitDimensions.putIfAbsent(canonical, dimension);
                canonicalUnit.putIfAbsent(canonical, canonical);
                unitFactor.putIfAbsent(canonical, 1.0);
                for (JsonNode alias : unit.path("aliases")) {
                    unitDimensions.putIfAbsent(alias.asText(), dimension);
                    canonicalUnit.putIfAbsent(alias.asText(), canonical);
                    unitFactor.putIfAbsent(alias.asText(), factor);
                }
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException("Unit dimensions are unavailable", ex); }
    }

    // ---------------------------------------------------------------- units

    /** True when both spellings measure the same quantity in the unit catalog (m and cm; rad and deg). */
    public boolean sameQuantity(String a, String b) {
        return canonicalUnit.getOrDefault(a, a).equals(canonicalUnit.getOrDefault(b, b));
    }

    /** A value written in one unit, expressed in another unit of the same quantity. */
    public double convert(double value, String from, String to) {
        return value * unitFactor.getOrDefault(from, 1.0) / unitFactor.getOrDefault(to, 1.0);
    }

    /** Every spelling the catalog accepts for the quantity this unit measures (the unit itself first). */
    public List<String> spellings(String unit) {
        String canonical = canonicalUnit.getOrDefault(unit, unit);
        List<String> result = new ArrayList<>(List.of(unit));
        canonicalUnit.forEach((spelling, target) -> { if (target.equals(canonical) && !result.contains(spelling)) result.add(spelling); });
        return result;
    }

    // ---------------------------------------------------------------- what a law allows

    /**
     * Inputs of a law that may follow a value changing during the run. The reviewer declares it per input
     * ("timeVarying": true); the declaration is honoured only when no result or rate that uses the input also
     * uses elapsed time, because such an expression is a solution for a constant input, not a law at an instant.
     */
    public Set<String> changingInputs(JsonNode capability) {
        JsonNode math = capability.path("execution").path("math");
        Set<String> inputs = new LinkedHashSet<>(), declared = new LinkedHashSet<>(), states = new HashSet<>(), clocks = new HashSet<>(Set.of("t"));
        for (JsonNode input : capability.path("canonicalInputs")) {
            inputs.add(input.path("key").asText());
            if (input.path("timeVarying").asBoolean(false)) declared.add(input.path("key").asText());
        }
        math.path("initial").fieldNames().forEachRemaining(states::add);
        math.path("rates").fields().forEachRemaining(rate -> { if (rate.getValue().isNumber() && rate.getValue().asDouble() != 0) clocks.add(rate.getKey()); });
        for (String phase : List.of("outputs", "rates")) {
            JsonNode expressions = math.path(phase);
            expressions.fields().forEachRemaining(expression -> {
                Set<String> symbols = new HashSet<>();
                symbols(expression.getValue(), expressions, states, inputs, new HashSet<>(Set.of(expression.getKey())), symbols, 0);
                if (symbols.stream().anyMatch(clocks::contains)) declared.removeAll(symbols);
            });
        }
        return declared;
    }

    private void symbols(JsonNode ast, JsonNode expressions, Set<String> states, Set<String> inputs, Set<String> seen,
            Set<String> into, int depth) {
        require(depth <= maxDepth * 4, "Cấu trúc phương trình vượt quá giới hạn xử lý");
        if (ast.isTextual()) {
            String name = ast.asText();
            if (!states.contains(name) && !inputs.contains(name) && expressions.has(name) && seen.add(name))
                symbols(expressions.get(name), expressions, states, inputs, seen, into, depth + 1);
            else into.add(name);
        } else if (ast.isArray()) for (int i = 1; i < ast.size(); i++) symbols(ast.get(i), expressions, states, inputs, seen, into, depth + 1);
    }

    // ---------------------------------------------------------------- the plan, bound to laws

    /** When a participant ends: the watched result passes the value in the given direction. */
    private record Until(String sourceId, String key, boolean rising, double value) { }

    /**
     * One participant: its law, the inputs that are numbers, the inputs that read another participant's result
     * (with the factor between the two units), and its place in the sequence of stages.
     */
    private record Bound(String id, JsonNode capability, Map<String, Double> constants, Map<String, String> references,
            Map<String, Double> referenceFactor, Set<String> changing, String after, Until until) {
        JsonNode math() { return capability.path("execution").path("math"); }
        double absTolerance() { return capability.path("validation").path("absoluteTolerance").asDouble(0); }
        double relTolerance() { return capability.path("validation").path("relativeTolerance").asDouble(0); }
        /** The same law on its own, every input fixed to a number. */
        Bound alone(Map<String, Double> inputs) { return new Bound(id, capability, inputs, Map.of(), Map.of(), Set.of(), null, null); }
    }

    private static JsonNode output(JsonNode capability, String key) {
        for (JsonNode candidate : capability.path("outputs")) if (candidate.path("key").asText().equals(key)) return candidate;
        return null;
    }

    private static final int MIN_STEPS = 200;
    private static final int MAX_BASE_STEPS = 1000;
    private static final int MAX_EVENTS_PER_STEP = 64;
    private static final int EVENT_BISECTIONS = 60;

    public ObjectNode compute(JsonNode definition, JsonNode spec, JsonNode overrides) {
        Map<String, Double> parameters = new LinkedHashMap<>();
        Map<String, String> parameterUnits = new LinkedHashMap<>();
        for (JsonNode parameter : spec.path("parameters")) {
            String name = parameter.path("name").asText();
            double value = overrides.has(name) ? overrides.path(name).asDouble(Double.NaN)
                    : parameter.path("value").asDouble(Double.NaN);
            require(!name.isBlank() && !parameters.containsKey(name) && Double.isFinite(value), "Tham số không hợp lệ");
            require(value >= parameter.path("min").asDouble(-Double.MAX_VALUE)
                    && value <= parameter.path("max").asDouble(Double.MAX_VALUE), "Tham số nằm ngoài phạm vi cho phép: " + name);
            parameters.put(name, value);
            parameterUnits.put(name, parameter.path("unit").asText());
        }
        overrides.fieldNames().forEachRemaining(name -> require(parameters.containsKey(name), "Tham số không xác định: " + name));
        double duration = spec.path("durationSeconds").asDouble(Double.NaN);
        if (spec.hasNonNull("durationParameter")) {
            String key = spec.path("durationParameter").asText();
            require(parameters.containsKey(key), "Tham số thời gian không xác định");
            for (JsonNode parameter : spec.path("parameters")) if (parameter.path("name").asText().equals(key)) {
                String unit = parameter.path("unit").asText();
                require(sameQuantity(unit, "s") && close(convert(parameter.path("value").asDouble(), unit, "s"), duration),
                        "Tham số thời gian phải khớp thời lượng ban đầu và dùng đơn vị thời gian");
            }
            duration = convert(parameters.get(key), parameterUnits.get(key), "s");
        }
        require(Double.isFinite(duration) && duration > 0 && duration <= maxDuration, "Thời lượng mô phỏng không hợp lệ");
        List<Bound> bindings = bind(definition, spec, parameters, parameterUnits);
        // Physical time scales range from milliseconds (AC, RC) to years (decay, orbits).
        // Start from a presentation-friendly resolution and refine (h -> h/2) only while
        // the reference / refinement checks fail, within the configured sample budget.
        int budgetSteps = (int) Math.max(0, (maxSamples / Math.max(1, bindings.size()) - 1) / 2);
        int steps = (int) Math.max(Math.min(MIN_STEPS, budgetSteps),
                Math.min(Math.min((double) budgetSteps, MAX_BASE_STEPS), Math.ceil(duration / defaultStep)));
        require(steps > 0 && (long) (steps * 2 + 1) * Math.max(1, bindings.size()) <= maxSamples,
                "Dữ liệu diễn tiến vượt quá giới hạn số mẫu. Vui lòng giảm thời lượng mô phỏng");
        boolean coverage = spec.path("physicsCoverage").asText().equals("COMPLETE");
        ObjectNode result = run(coverage, bindings, duration, steps);
        while ("FLAGGED".equals(result.path("validation").path("status").asText()) && steps * 2L <= budgetSteps) {
            steps *= 2;
            result = run(coverage, bindings, duration, steps);
        }
        return result;
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(Math.abs(a), Math.abs(b));
    }

    private List<Bound> bind(JsonNode definition, JsonNode spec, Map<String, Double> parameters, Map<String, String> parameterUnits) {
        Map<String, JsonNode> models = new LinkedHashMap<>();
        Map<String, JsonNode> capabilities = new LinkedHashMap<>();
        for (JsonNode model : spec.path("physicsModels")) {
            String id = model.path("id").asText();
            require(!id.isBlank() && models.put(id, model) == null, "Định danh mô hình bị trùng hoặc bị thiếu");
            require(models.size() <= maxParticipants, "Số lượng vật thể vượt quá giới hạn cho phép");
            JsonNode capability = null;
            for (JsonNode candidate : definition.path("capabilities"))
                if (candidate.path("capabilityId").asText().equals(model.path("capabilityId").asText())) capability = candidate;
            require(capability != null && capability.path("execution").path("math").isObject(),
                    "Chức năng chưa có hệ phương trình thực thi được phê duyệt");
            checkDimensions(capability);
            capabilities.put(id, capability);
        }
        List<Bound> bindings = new ArrayList<>();
        for (JsonNode model : models.values()) {
            String id = model.path("id").asText();
            JsonNode capability = capabilities.get(id);
            Map<String, Double> constants = new LinkedHashMap<>(), factors = new LinkedHashMap<>();
            Map<String, String> references = new LinkedHashMap<>();
            for (JsonNode input : capability.path("canonicalInputs")) {
                String key = input.path("key").asText(), unit = input.path("unit").asText();
                JsonNode binding = model.path("inputs").path(key);
                if (binding.isTextual() && !parameters.containsKey(binding.asText())) {
                    JsonNode source = source(binding.asText(), models.keySet(), capabilities);
                    require(sameQuantity(source.path("unit").asText(), unit), "Đầu vào phải sử dụng đơn vị SI chuẩn: " + key
                            + " cần đơn vị " + unit + ", kết quả " + binding.asText() + " có đơn vị " + source.path("unit").asText());
                    references.put(key, binding.asText());
                    factors.put(key, convert(1, source.path("unit").asText(), unit));
                    continue;
                }
                double value;
                if (binding.isTextual()) {
                    String parameterUnit = parameterUnits.get(binding.asText());
                    require(sameQuantity(parameterUnit, unit), "Đầu vào phải sử dụng đơn vị SI chuẩn: " + key + " cần đơn vị "
                            + unit + ", tham số " + binding.asText() + " đang dùng " + parameterUnit);
                    value = convert(parameters.get(binding.asText()), parameterUnit, unit);
                } else value = binding.isNumber() ? binding.asDouble() : input.path("defaultValue").asDouble(Double.NaN);
                require(Double.isFinite(value), "Thiếu đầu vào chuẩn: " + key);
                require(value >= input.path("min").asDouble(-Double.MAX_VALUE)
                        && value <= input.path("max").asDouble(Double.MAX_VALUE), "Giá trị nằm ngoài miền tính toán của bộ giải: " + key);
                constants.put(key, value);
            }
            String after = model.path("after").asText("");
            if (!after.isEmpty()) {
                require(models.containsKey(after) && !after.equals(id), "Giai đoạn đứng trước không xác định: " + after
                        + " (id hiện có: " + String.join(", ", models.keySet()) + ")");
                require(models.get(after).path("until").isObject(), "Vật " + id + " bắt đầu sau " + after
                        + " nên " + after + " phải có điều kiện kết thúc (until)");
            }
            Until until = null;
            if (model.path("until").isObject()) {
                JsonNode rule = model.path("until");
                String field = rule.path("field").asText(), direction = rule.path("direction").asText();
                JsonNode watched = source(field, models.keySet(), capabilities);
                require(direction.equals("rising") || direction.equals("falling"),
                        "Điều kiện kết thúc của " + id + " cần direction là rising hoặc falling");
                double value;
                if (rule.path("value").isTextual()) {
                    String name = rule.path("value").asText();
                    require(parameters.containsKey(name), "Tham số không xác định: " + name);
                    require(sameQuantity(parameterUnits.get(name), watched.path("unit").asText()), "Điều kiện kết thúc của " + id
                            + ": tham số " + name + " phải cùng loại đơn vị với " + field + " (" + watched.path("unit").asText() + ")");
                    value = convert(parameters.get(name), parameterUnits.get(name), watched.path("unit").asText());
                } else value = rule.path("value").asDouble(Double.NaN);
                require(Double.isFinite(value), "Điều kiện kết thúc của " + id + " thiếu giá trị");
                int dot = field.lastIndexOf('.');
                until = new Until(field.substring(0, dot), field.substring(dot + 1), direction.equals("rising"), value);
            }
            bindings.add(new Bound(id, capability, constants, references, factors, changingInputs(capability),
                    after.isEmpty() ? null : after, until));
        }
        for (Bound bound : bindings) {
            Set<String> chain = new HashSet<>();
            for (String at = bound.id; at != null; at = bindings.get(indexOf(bindings, at)).after)
                require(chain.add(at), "Các giai đoạn nối tiếp nhau theo vòng tròn: " + bound.id);
        }
        return bindings;
    }

    private static int indexOf(List<Bound> bindings, String id) {
        for (int i = 0; i < bindings.size(); i++) if (bindings.get(i).id.equals(id)) return i;
        return -1;
    }

    /** The declared result "&lt;participantId&gt;.&lt;outputKey&gt;" (or "&lt;participantId&gt;.t", the time since that participant started). */
    private JsonNode source(String reference, Set<String> ids, Map<String, JsonNode> capabilities) {
        int dot = reference.lastIndexOf('.');
        String id = dot > 0 ? reference.substring(0, dot) : "", key = reference.substring(dot + 1);
        require(ids.contains(id), "Tham số đầu vào không xác định: " + reference
                + " (dùng tên một tham số, hoặc id của vật khác, dấu chấm và tên đầu ra; id hiện có: " + String.join(", ", ids) + ")");
        if (key.equals("t")) return json.createObjectNode().put("key", "t").put("unit", "s");
        JsonNode output = output(capabilities.get(id), key);
        List<String> available = new ArrayList<>();
        for (JsonNode candidate : capabilities.get(id).path("outputs")) available.add(candidate.path("key").asText());
        require(output != null, "Tham số đầu vào không xác định: " + reference + " (đầu ra của " + id + ": " + String.join(", ", available) + ")");
        return output;
    }

    // ---------------------------------------------------------------- integration of the whole plan

    /** What one integration produced: values on the uniform grid, the instants where a stage changed, and when each participant ran. */
    private static final class Trajectory {
        final List<Double> times = new ArrayList<>();
        /** per participant: grid index → results (null while it has not started). */
        final Map<String, List<Map<String, Double>>> outputs = new LinkedHashMap<>();
        /** per participant: grid index → rates and inputs at that instant (null unless running). */
        final Map<String, List<Map<String, Double>>> rates = new LinkedHashMap<>();
        final Map<String, List<Map<String, Double>>> inputs = new LinkedHashMap<>();
        final Map<String, Double> start = new LinkedHashMap<>(), end = new LinkedHashMap<>();
        final Map<String, Map<String, Double>> startInputs = new LinkedHashMap<>(), startOutputs = new LinkedHashMap<>();
        /** Instants between grid points where a participant ended: time → every participant's results. */
        final List<Map.Entry<Double, Map<String, Map<String, Double>>>> events = new ArrayList<>();
        double endTime;
    }

    /** The plan at one instant: who is running with which state, who has ended with which final results. */
    private final class Simulation {
        final List<Bound> bounds;
        final Map<String, Bound> byId = new LinkedHashMap<>();
        Map<String, Map<String, Double>> states = new LinkedHashMap<>();
        final Map<String, Map<String, Double>> finished = new LinkedHashMap<>();
        final Map<String, Double> started = new LinkedHashMap<>();
        double time;
        private final Map<String, Double> memo = new LinkedHashMap<>();
        private final Set<String> resolving = new LinkedHashSet<>();

        Simulation(List<Bound> bounds) {
            this.bounds = bounds;
            bounds.forEach(bound -> byId.put(bound.id, bound));
        }

        void at(Map<String, Map<String, Double>> states, double time) {
            this.states = states;
            this.time = time;
            memo.clear();
        }

        boolean running(String id) { return states.containsKey(id); }

        double localTime(String id) { return running(id) ? time - started.get(id) : 0; }

        /** A named expression of one participant, evaluated once per instant; expressions that need each other in a circle are rejected. */
        double named(Bound bound, String phase, String key) {
            String slot = phase + "\u0000" + bound.id + "\u0000" + key;
            Double known = memo.get(slot);
            if (known != null) return known;
            JsonNode expressions = bound.math().path(phase);
            require(expressions.has(key), "Đại lượng trong phương trình không xác định: " + key);
            require(resolving.add(slot) && resolving.size() <= maxDepth * 8, "Các phương trình phụ thuộc vòng hoặc vượt quá độ sâu cho phép");
            double value = evaluate(expressions.get(key), name -> symbol(bound, phase, name), 0, new int[]{0});
            resolving.remove(slot);
            memo.put(slot, value);
            return value;
        }

        double symbol(Bound bound, String phase, String name) {
            if (name.equals("t")) return localTime(bound.id);
            if (!phase.equals("initial")) {
                if (running(bound.id)) { Double state = states.get(bound.id).get(name); if (state != null) return state; }
                else if (bound.math().path("initial").has(name)) return named(bound, "initial", name);
            }
            if (bound.constants.containsKey(name)) return bound.constants.get(name);
            if (bound.references.containsKey(name)) return input(bound, name);
            return named(bound, phase, name);
        }

        double input(Bound bound, String key) {
            String reference = bound.references.get(key);
            int dot = reference.lastIndexOf('.');
            return result(reference.substring(0, dot), reference.substring(dot + 1)) * bound.referenceFactor.get(key);
        }

        /** A participant's result now: its final value once it has ended, the value it will start from before it starts. */
        double result(String id, String key) {
            if (key.equals("t")) return finished.containsKey(id) ? finished.get(id).get("t") : localTime(id);
            if (finished.containsKey(id)) return finished.get(id).get(key);
            return named(byId.get(id), "outputs", key);
        }

        Map<String, Double> results(Bound bound) {
            if (finished.containsKey(bound.id)) return finished.get(bound.id);
            Map<String, Double> values = new LinkedHashMap<>();
            bound.math().path("outputs").fieldNames().forEachRemaining(key -> values.put(key, named(bound, "outputs", key)));
            return values;
        }

        Map<String, Double> inputs(Bound bound) {
            Map<String, Double> values = new LinkedHashMap<>(bound.constants);
            bound.references.keySet().forEach(key -> values.put(key, input(bound, key)));
            return values;
        }

        Map<String, Double> rates(Bound bound) {
            Map<String, Double> values = new LinkedHashMap<>();
            bound.math().path("rates").fieldNames().forEachRemaining(key -> values.put(key, named(bound, "rates", key)));
            return values;
        }

        void start(Bound bound) {
            Map<String, Double> state = new LinkedHashMap<>();
            bound.math().path("initial").fieldNames().forEachRemaining(key -> state.put(key, named(bound, "initial", key)));
            require(!state.isEmpty(), "Hệ phương trình chưa có trạng thái ban đầu");
            Map<String, Map<String, Double>> next = new LinkedHashMap<>(states);
            next.put(bound.id, state);
            started.put(bound.id, time);
            at(next, time);
        }

        void finish(Bound bound) {
            Map<String, Double> last = new LinkedHashMap<>(results(bound));
            last.put("t", localTime(bound.id));
            Map<String, Map<String, Double>> next = new LinkedHashMap<>(states);
            next.remove(bound.id);
            finished.put(bound.id, last);
            at(next, time);
        }

        /** How far the watched result is from the value that ends this participant; it ends when this reaches zero from the positive side. */
        double distance(Bound bound) {
            double value = result(bound.until.sourceId, bound.until.key) - bound.until.value;
            return bound.until.rising ? -value : value;
        }

        Map<String, Map<String, Double>> derivative(Map<String, Map<String, Double>> at, double t) {
            at(at, t);
            Map<String, Map<String, Double>> all = new LinkedHashMap<>();
            for (String id : at.keySet()) all.put(id, rates(byId.get(id)));
            return all;
        }

        Map<String, Map<String, Double>> advance(Map<String, Map<String, Double>> from, Map<String, Map<String, Double>> slope, double h) {
            Map<String, Map<String, Double>> all = new LinkedHashMap<>();
            from.forEach((id, state) -> {
                Map<String, Double> next = new LinkedHashMap<>();
                state.forEach((key, value) -> {
                    require(slope.get(id).containsKey(key), "Thiếu đạo hàm của trạng thái: " + key);
                    next.put(key, value + h * slope.get(id).get(key));
                });
                all.put(id, next);
            });
            return all;
        }

        /** One classical Runge–Kutta step of every running participant together. */
        Map<String, Map<String, Double>> step(Map<String, Map<String, Double>> from, double t, double h,
                Map<String, Map<String, Double>> k1) {
            Map<String, Map<String, Double>> k2 = derivative(advance(from, k1, h / 2), t + h / 2);
            Map<String, Map<String, Double>> k3 = derivative(advance(from, k2, h / 2), t + h / 2);
            Map<String, Map<String, Double>> k4 = derivative(advance(from, k3, h), t + h);
            Map<String, Map<String, Double>> all = new LinkedHashMap<>();
            from.forEach((id, state) -> {
                Map<String, Double> next = new LinkedHashMap<>();
                for (String key : state.keySet()) {
                    require(k1.get(id).containsKey(key) && k2.get(id).containsKey(key) && k3.get(id).containsKey(key)
                            && k4.get(id).containsKey(key), "Thiếu đạo hàm của trạng thái: " + key);
                    double value = state.get(key) + h / 6 * (k1.get(id).get(key) + 2 * k2.get(id).get(key)
                            + 2 * k3.get(id).get(key) + k4.get(id).get(key));
                    require(Double.isFinite(value), "Bộ giải trả về giá trị không xác định hoặc vô hạn");
                    next.put(key, value);
                }
                all.put(id, next);
            });
            return all;
        }
    }

    private Trajectory integrate(List<Bound> bounds, double duration, int steps) {
        Simulation sim = new Simulation(bounds);
        Trajectory out = new Trajectory();
        for (Bound bound : bounds) {
            out.outputs.put(bound.id, new ArrayList<>());
            out.rates.put(bound.id, new ArrayList<>());
            out.inputs.put(bound.id, new ArrayList<>());
        }
        sim.at(new LinkedHashMap<>(), 0);
        for (Bound bound : bounds) if (bound.after == null) begin(sim, out, bound);
        double h = duration / steps;
        boolean over = false;
        for (int i = 0; i <= steps && !over; i++) {
            double t = duration * i / steps;
            Map<String, Map<String, Double>> state = sim.states;
            Map<String, Map<String, Double>> k1 = null;
            if (i < steps) k1 = sim.derivative(state, t); else sim.at(state, t);
            out.times.add(t);
            for (Bound bound : bounds) {
                boolean running = sim.running(bound.id);
                out.outputs.get(bound.id).add(running || sim.finished.containsKey(bound.id) ? sim.results(bound) : null);
                out.rates.get(bound.id).add(running && k1 != null ? k1.get(bound.id) : null);
                out.inputs.get(bound.id).add(running ? sim.inputs(bound) : null);
            }
            out.endTime = t;
            if (i == steps) break;
            double remaining = h;
            for (int events = 0; ; events++) {
                Map<String, Double> before = new LinkedHashMap<>();
                for (Bound bound : bounds) if (bound.until != null && sim.running(bound.id)) before.put(bound.id, sim.distance(bound));
                Map<String, Map<String, Double>> next = sim.step(state, t, remaining, k1);
                sim.at(next, t + remaining);
                List<Bound> ending = crossed(sim, bounds, before);
                if (ending.isEmpty()) break;
                require(events < MAX_EVENTS_PER_STEP, "Quá nhiều giai đoạn kết thúc trong một bước tính; hãy tăng thời lượng hoặc tách các giai đoạn");
                // the earliest instant in this step at which one of them has reached its value
                double low = 0, high = remaining;
                for (int cut = 0; cut < EVENT_BISECTIONS && high - low > Math.ulp(t + high); cut++) {
                    double middle = (low + high) / 2;
                    sim.at(sim.step(state, t, middle, k1), t + middle);
                    if (crossed(sim, bounds, before).isEmpty()) low = middle; else high = middle;
                }
                sim.at(sim.step(state, t, high, k1), t + high);
                for (Bound bound : crossed(sim, bounds, before)) {
                    sim.finish(bound);
                    out.end.put(bound.id, sim.time);
                    for (Bound follower : bounds) if (bound.id.equals(follower.after)) begin(sim, out, follower);
                }
                Map<String, Map<String, Double>> snapshot = new LinkedHashMap<>();
                for (Bound bound : bounds) if (sim.running(bound.id) || sim.finished.containsKey(bound.id)) snapshot.put(bound.id, sim.results(bound));
                out.events.add(Map.entry(sim.time, snapshot));
                out.endTime = sim.time;
                if (sim.states.isEmpty()) { over = true; break; }
                t = sim.time;
                remaining = duration * (i + 1) / steps - t;
                state = sim.states;
                if (remaining <= 0) break;
                k1 = sim.derivative(state, t);
            }
        }
        // before it starts, a participant shows the state it will start from
        sim.at(sim.states, out.endTime);
        for (Bound bound : bounds) {
            Map<String, Double> first = out.startOutputs.containsKey(bound.id) ? out.startOutputs.get(bound.id) : sim.results(bound);
            List<Map<String, Double>> series = out.outputs.get(bound.id);
            for (int i = 0; i < series.size(); i++) if (series.get(i) == null) series.set(i, first);
            for (var event : out.events) event.getValue().putIfAbsent(bound.id, first);
        }
        return out;
    }

    private void begin(Simulation sim, Trajectory out, Bound bound) {
        sim.start(bound);
        out.start.put(bound.id, sim.time);
        out.startInputs.put(bound.id, sim.inputs(bound));
        out.startOutputs.put(bound.id, sim.results(bound));
    }

    private List<Bound> crossed(Simulation sim, List<Bound> bounds, Map<String, Double> before) {
        List<Bound> ending = new ArrayList<>();
        for (Bound bound : bounds)
            if (before.containsKey(bound.id) && sim.running(bound.id) && before.get(bound.id) > 0 && sim.distance(bound) <= 0) ending.add(bound);
        return ending;
    }

    // ---------------------------------------------------------------- verification and the timeline

    private ObjectNode run(boolean coverage, List<Bound> bindings, double duration, int steps) {
        ObjectNode result = json.createObjectNode();
        ObjectNode validation = result.putObject("validation");
        var flags = validation.putArray("flags");
        var assumptions = validation.putArray("assumptions");
        var evidence = validation.putArray("convergenceEvidence");
        ObjectNode invariants = validation.putObject("invariantResults");
        validation.put("solverVersion", "schema-ast-rk4/1.0");
        validation.put("formulaSource", "APPROVED_TOPIC_SCHEMA");
        validation.put("verificationScope", "Các đại lượng chuẩn đã được liên kết; các giả định vẫn được hiển thị để kiểm tra");
        Trajectory coarse = integrate(bindings, duration, steps);
        Trajectory fine = integrate(bindings, duration, steps * 2);
        boolean allReference = !bindings.isEmpty();
        boolean numericalEvidence = !bindings.isEmpty();
        boolean passed = true;
        double absoluteError = 0;
        double relativeError = 0;
        double maxResidual = 0;
        int benchmarksChecked = 0;
        double h = duration / steps;
        for (Bound bound : bindings) {
            JsonNode math = bound.math();
            double absTolerance = bound.capability.path("validation").path("absoluteTolerance").asDouble(Double.NaN);
            double relTolerance = bound.capability.path("validation").path("relativeTolerance").asDouble(Double.NaN);
            require(Double.isFinite(absTolerance) && absTolerance >= 0 && Double.isFinite(relTolerance) && relTolerance >= 0,
                    "Mô hình cần có sai số cho phép để kiểm định");
            List<Map<String, Double>> samples = coarse.outputs.get(bound.id), liveInputs = coarse.inputs.get(bound.id);
            Map<String, Double> startInputs = coarse.startInputs.get(bound.id);
            double start = coarse.start.getOrDefault(bound.id, Double.NaN);
            int first = -1, last = -1;
            for (int i = 0; i < samples.size(); i++) if (liveInputs.get(i) != null) { if (first < 0) first = i; last = i; }
            // an input that reads another participant either accepts a changing value or must stay what it was at the start
            boolean driven = false;
            for (int i = first; first >= 0 && i <= last; i++) for (String key : bound.references.keySet()) {
                double value = liveInputs.get(i).get(key), initial = startInputs.get(key);
                Bound source = bindings.get(indexOf(bindings, bound.references.get(key).substring(0, bound.references.get(key).lastIndexOf('.'))));
                if (Math.abs(value - initial) <= source.absTolerance() + source.relTolerance() * Math.abs(initial)) continue;
                require(bound.changing.contains(key), "Kết quả " + bound.references.get(key) + " thay đổi theo thời gian nên không dùng làm đầu vào "
                        + key + " của " + bound.id + " được: định luật này chỉ đúng khi " + key + " không đổi");
                driven = true;
            }
            boolean hasReference = math.path("closedForm").isObject() && !math.path("closedForm").isEmpty() && !driven;
            allReference &= hasReference;
            Map<String, Double> initialInvariants = new LinkedHashMap<>();
            for (int i = first; first >= 0 && i <= last; i++) {
                Map<String, Double> sample = samples.get(i);
                Map<String, Double> reference = hasReference ? evaluateMap(math.path("closedForm"), startInputs,
                        Map.of("t", coarse.times.get(i) - start)) : fineSample(fine, bound.id, i * 2);
                if (reference != null) for (String key : sample.keySet()) {
                    require(reference.containsKey(key), "Dữ liệu đối chiếu thiếu đầu ra chuẩn: " + key);
                    double error = Math.abs(sample.get(key) - reference.get(key));
                    double scale = Math.max(Math.abs(sample.get(key)), Math.abs(reference.get(key)));
                    absoluteError = Math.max(absoluteError, error);
                    relativeError = Math.max(relativeError, error / Math.max(scale, absTolerance > 0 ? absTolerance : Double.MIN_NORMAL));
                    passed &= error <= absTolerance + relTolerance * scale;
                }
                if (!driven) {
                    Map<String, Double> inv = evaluateMap(math.path("invariants"), startInputs, sample);
                    if (i == first) initialInvariants.putAll(inv);
                    for (var invariant : inv.entrySet()) {
                        double baseline = initialInvariants.get(invariant.getKey());
                        boolean valid = Math.abs(invariant.getValue() - baseline) <= absTolerance + relTolerance * Math.abs(baseline);
                        String name = bound.id + "." + invariant.getKey();
                        invariants.put(name, invariants.path(name).asBoolean(true) && valid);
                        passed &= valid;
                    }
                }
                if (i >= first + 2 && i <= last - 2 && coarse.times.get(i - 2) >= start) {
                    for (var rate : coarse.rates.get(bound.id).get(i).entrySet()) {
                        String key = rate.getKey();
                        require(sample.containsKey(key), "Kết quả bộ giải thiếu trạng thái phương trình vi phân: " + key);
                        double derivative = (-samples.get(i + 2).get(key) + 8 * samples.get(i + 1).get(key)
                                - 8 * samples.get(i - 1).get(key) + samples.get(i - 2).get(key)) / (12 * duration / steps);
                        double residual = Math.abs(derivative - rate.getValue());
                        maxResidual = Math.max(maxResidual, residual);
                        passed &= residual <= absTolerance + relTolerance * Math.abs(rate.getValue());
                    }
                }
            }
            int declaredBenchmarks = 0;
            for (JsonNode benchmark : bound.capability.path("validation").path("benchmarks")) {
                if (startInputs == null || !benchmark.path("expectedOutputs").isObject() || benchmark.path("expectedOutputs").isEmpty()) continue;
                Map<String, Double> benchmarkInputs = new LinkedHashMap<>(startInputs);
                benchmark.path("inputs").fields().forEachRemaining(field -> benchmarkInputs.put(field.getKey(), field.getValue().asDouble()));
                double benchmarkDuration = benchmark.path("durationSeconds").asDouble(1);
                require(benchmarkDuration > 0 && benchmarkDuration <= maxDuration, "Thời lượng kiểm chuẩn không hợp lệ");
                int benchmarkSteps = (int) Math.ceil(benchmarkDuration / defaultStep);
                require(benchmarkSteps <= maxSamples, "Số mẫu kiểm chuẩn vượt quá giới hạn");
                var benchmarkSamples = integrate(List.of(bound.alone(benchmarkInputs)), benchmarkDuration, benchmarkSteps).outputs.get(bound.id);
                Map<String, Double> end = benchmarkSamples.get(benchmarkSamples.size() - 1);
                var expected = benchmark.path("expectedOutputs").fields();
                while (expected.hasNext()) {
                    var field = expected.next();
                    require(end.containsKey(field.getKey()), "Bài kiểm chuẩn tham chiếu đến đầu ra không xác định");
                    double value = field.getValue().asDouble(Double.NaN);
                    passed &= Double.isFinite(value) && Math.abs(end.get(field.getKey()) - value) <= absTolerance + relTolerance * Math.abs(value);
                }
                declaredBenchmarks++; benchmarksChecked++;
            }
            boolean hasInvariants = math.path("invariants").isObject() && !math.path("invariants").isEmpty();
            if (driven) {
                // the law itself is checked on its own with the inputs it started from; the coupled run by halving the step
                String law = run(true, List.of(bound.alone(startInputs)), Math.max(coarse.times.get(last) - start, h), Math.max(4, last - first))
                        .path("validation").path("status").asText();
                passed &= !law.equals("FLAGGED");
                numericalEvidence &= law.startsWith("VERIFIED") && steps >= 4;
                evidence.add(bound.id + ": inputs change during the run; the law verified on its own (" + law
                        + "), the coupled run by step refinement h -> h/2 over " + (last - first + 1) + " checkpoints");
            } else {
                numericalEvidence &= hasReference || (hasInvariants && declaredBenchmarks > 0 && steps >= 4);
                evidence.add(bound.id + ": compared " + (first < 0 ? 0 : last - first + 1) + " checkpoints using "
                        + (hasReference ? "independent approved closed-form AST" : "step refinement h -> h/2 and declared invariants"));
            }
            for (JsonNode assumption : bound.capability.path("assumptions")) assumptions.add(assumption.asText());
        }
        boolean staged = bindings.stream().anyMatch(bound -> bound.after != null || bound.until != null);
        ObjectNode timeline = result.putObject("solverTimeline");
        timeline.put("durationSeconds", staged ? coarse.endTime : duration);
        var frames = timeline.putArray("frames");
        int event = 0;
        for (int i = 0; i < coarse.times.size(); i++) {
            double t = coarse.times.get(i);
            for (; event < coarse.events.size() && coarse.events.get(event).getKey() < t; event++)
                frame(frames, coarse.events.get(event).getKey(), bindings, coarse.events.get(event).getValue());
            if (event < coarse.events.size() && coarse.events.get(event).getKey() == t) event++;
            Map<String, Map<String, Double>> values = new LinkedHashMap<>();
            for (Bound bound : bindings) values.put(bound.id, coarse.outputs.get(bound.id).get(i));
            frame(frames, t, bindings, values);
        }
        for (; event < coarse.events.size(); event++) frame(frames, coarse.events.get(event).getKey(), bindings, coarse.events.get(event).getValue());
        if (staged) {
            ObjectNode phases = timeline.putObject("phases");
            for (Bound bound : bindings) {
                ObjectNode phase = phases.putObject(bound.id);
                if (coarse.start.containsKey(bound.id)) phase.put("start", coarse.start.get(bound.id)); else phase.putNull("start");
                if (coarse.end.containsKey(bound.id)) phase.put("end", coarse.end.get(bound.id)); else phase.putNull("end");
            }
        }
        String status = !passed ? "FLAGGED" : !coverage || !numericalEvidence ? "VISUAL_ONLY_UNVERIFIED"
                : allReference ? "VERIFIED_ANALYTICAL" : "VERIFIED_NUMERICAL";
        if (!coverage) flags.add("Một phần nội dung vật lý chưa được liên kết với phương trình thực thi đã phê duyệt.");
        if (!numericalEvidence && !bindings.isEmpty()) flags.add("Minh chứng tính toán số chưa đầy đủ.");
        if (!passed) flags.add("Kết quả đối chiếu hoặc kiểm tra đại lượng bảo toàn không đạt; mô phỏng chưa được xác minh.");
        validation.put("status", status);
        validation.put("executionMethod", bindings.isEmpty() ? "VISUAL_ONLY" : "NUMERICAL");
        validation.put("solverMethod", "RK4");
        validation.put("verificationMethod", allReference ? "CLOSED_FORM_CHECKPOINTS" : "STEP_REFINEMENT_AND_INVARIANTS");
        validation.put("absoluteError", absoluteError);
        validation.put("relativeError", relativeError);
        validation.put("maxOdeResidual", maxResidual);
        validation.put("benchmarkSummary", "Đã kiểm tra " + benchmarksChecked + " liên kết kiểm chuẩn được phê duyệt");
        if (allReference) validation.put("referenceSolverVersion", "schema-ast-closed-form/1.0");
        return result;
    }

    private static Map<String, Double> fineSample(Trajectory fine, String id, int index) {
        List<Map<String, Double>> series = fine.outputs.get(id);
        return index < series.size() && fine.inputs.get(id).get(index) != null ? series.get(index) : null;
    }

    private void frame(com.fasterxml.jackson.databind.node.ArrayNode frames, double t, List<Bound> bindings,
            Map<String, Map<String, Double>> values) {
        ObjectNode frame = frames.addObject();
        frame.put("t", t);
        ObjectNode fields = frame.putObject("values");
        fields.put("t", t);
        for (Bound bound : bindings) {
            String prefix = bound.id + ".";
            values.get(bound.id).forEach((key, value) -> { if (!key.equals("t")) fields.put(prefix + key, value); });
        }
    }

    // ---------------------------------------------------------------- dimensions

    private void checkDimensions(JsonNode capability) {
        Map<String, Map<String, Double>> symbols = new LinkedHashMap<>();
        symbols.put("t", Map.of("T", 1.0));
        for (String group : List.of("canonicalInputs", "outputs")) for (JsonNode quantity : capability.path(group)) {
            String unit = quantity.path("unit").asText();
            require(unitDimensions.containsKey(unit), "Thứ nguyên đơn vị chuẩn không xác định: " + unit);
            symbols.put(quantity.path("key").asText(), unitDimensions.get(unit));
        }
        JsonNode math = capability.path("execution").path("math");
        for (String phase : List.of("initial", "rates", "outputs", "closedForm", "invariants")) {
            var equations = math.path(phase).fields();
            while (equations.hasNext()) {
                var equation = equations.next();
                Map<String, Double> actual = dimension(equation.getValue(), symbols, 0, new int[]{0});
                if (!phase.equals("invariants")) {
                    require(symbols.containsKey(equation.getKey()), "Đơn vị của trạng thái hoặc đầu ra chưa được khai báo: " + equation.getKey());
                    Map<String, Double> expected = symbols.get(equation.getKey());
                    if (phase.equals("rates")) expected = combine(expected, Map.of("T", 1.0), -1);
                    require(actual == null || actual.equals(expected), "Thứ nguyên không khớp tại " + phase + "." + equation.getKey());
                }
            }
        }
    }

    private static final Set<String> UNARY = Set.of("neg", "sin", "cos", "tan", "sqrt", "exp", "abs", "log", "asin", "acos", "atan", "sign", "floor");
    private static final Set<String> BINARY = Set.of("add", "sub", "mul", "div", "pow", "min", "max", "atan2", "mod");

    /** Operand count of an operator: one, two, or three for "if" (condition, value when positive, value otherwise). */
    private static int arity(String op) {
        if (UNARY.contains(op)) return 1;
        if (BINARY.contains(op)) return 2;
        if (op.equals("if")) return 3;
        throw ApiException.unprocessable("Phép toán số học không được hỗ trợ: " + op);
    }

    private Map<String, Double> dimension(JsonNode ast, Map<String, Map<String, Double>> symbols, int depth, int[] nodes) {
        require(depth <= maxDepth && ++nodes[0] <= maxNodes, "Phép kiểm tra thứ nguyên vượt quá giới hạn xử lý");
        if (ast.isNumber()) return ast.asDouble() == 0 ? null : Map.of();
        if (ast.isTextual()) { require(symbols.containsKey(ast.asText()), "Thứ nguyên đại lượng chưa được khai báo: " + ast.asText()); return symbols.get(ast.asText()); }
        require(ast.isArray() && ast.size() >= 2, "Cấu trúc phương trình không hợp lệ");
        String op = ast.path(0).asText();
        require(ast.size() == arity(op) + 1, "Cấu trúc phương trình không hợp lệ");
        Map<String, Double> a = dimension(ast.get(1), symbols, depth + 1, nodes);
        Map<String, Double> b = ast.size() > 2 ? dimension(ast.get(2), symbols, depth + 1, nodes) : Map.of();
        return switch (op) {
            case "add", "sub", "min", "max", "mod" -> { require(a == null || b == null || a.equals(b), "Không thể cộng các đại lượng khác thứ nguyên"); yield a == null ? b : a; }
            case "mul" -> a == null || b == null ? null : combine(a, b, 1);
            case "div" -> a == null ? null : combine(a, b == null ? Map.of() : b, -1);
            case "neg", "abs" -> a;
            case "sqrt", "pow" -> {
                double exponent = op.equals("sqrt") ? 0.5 : ast.path(2).asDouble(Double.NaN);
                require(Double.isFinite(exponent) && (b == null || b.isEmpty()), "Số mũ thứ nguyên phải là hằng số");
                Map<String, Double> result = new LinkedHashMap<>();
                if (a != null) a.forEach((key, value) -> { if (value * exponent != 0) result.put(key, value * exponent); });
                yield result;
            }
            case "sin", "cos", "tan", "exp", "log", "asin", "acos", "atan", "floor" -> {
                require(a == null || a.isEmpty(), "Đầu vào hàm siêu việt phải không có thứ nguyên; góc dùng đơn vị radian"); yield Map.of(); }
            case "sign" -> Map.of();
            case "atan2" -> { require(a == null || b == null || a.equals(b), "Hai thành phần của atan2 phải cùng thứ nguyên"); yield Map.of(); }
            case "if" -> {
                Map<String, Double> otherwise = dimension(ast.get(3), symbols, depth + 1, nodes);
                require(b == null || otherwise == null || b.equals(otherwise), "Hai nhánh của điều kiện phải cùng thứ nguyên");
                yield b == null ? otherwise : b;
            }
            default -> throw ApiException.unprocessable("Phép toán thứ nguyên không xác định: " + op);
        };
    }

    private Map<String, Double> combine(Map<String, Double> a, Map<String, Double> b, double sign) {
        Map<String, Double> result = new LinkedHashMap<>(a);
        b.forEach((key, value) -> result.merge(key, sign * value, Double::sum));
        result.entrySet().removeIf(entry -> entry.getValue() == 0);
        return result;
    }

    // ---------------------------------------------------------------- expressions

    /** A map of expressions over fixed inputs and a given state (closed forms, invariants). */
    private Map<String, Double> evaluateMap(JsonNode expressions, Map<String, Double> inputs, Map<String, Double> state) {
        Map<String, Double> result = new LinkedHashMap<>();
        List<String> resolving = new ArrayList<>();
        class Resolver implements ToDoubleFunction<String> {
            public double applyAsDouble(String key) {
                if (state.containsKey(key)) return state.get(key);
                if (inputs.containsKey(key)) return inputs.get(key);
                return evaluateKey(key);
            }
            double evaluateKey(String key) {
                if (result.containsKey(key)) return result.get(key);
                require(expressions.has(key), "Đại lượng trong phương trình không xác định: " + key);
                require(!resolving.contains(key) && resolving.size() < maxDepth, "Các phương trình phụ thuộc vòng hoặc vượt quá độ sâu cho phép");
                resolving.add(key);
                double value = evaluate(expressions.get(key), this, 0, new int[]{0});
                resolving.removeLast(); result.put(key, value); return value;
            }
        }
        Resolver resolver = new Resolver();
        expressions.fieldNames().forEachRemaining(resolver::evaluateKey);
        return result;
    }

    private double evaluate(JsonNode ast, ToDoubleFunction<String> lookup, int depth, int[] nodes) {
        require(depth <= maxDepth && ++nodes[0] <= maxNodes, "Cấu trúc phương trình vượt quá giới hạn xử lý");
        if (ast.isNumber()) return ast.asDouble();
        if (ast.isTextual()) return lookup.applyAsDouble(ast.asText());
        require(ast.isArray() && ast.size() >= 2, "Cấu trúc biểu thức số học không hợp lệ");
        String op = ast.path(0).asText();
        require(ast.size() == arity(op) + 1, "Số toán hạng trong biểu thức không hợp lệ");
        double a = evaluate(ast.get(1), lookup, depth + 1, nodes);
        // only the chosen branch of a condition is evaluated, so the other may be undefined there
        if (op.equals("if")) return evaluate(ast.get(a > 0 ? 2 : 3), lookup, depth + 1, nodes);
        double b = ast.size() > 2 ? evaluate(ast.get(2), lookup, depth + 1, nodes) : 0;
        double value = switch (op) {
            case "add" -> a + b;
            case "sub" -> a - b;
            case "mul" -> a * b;
            case "div" -> a / b;
            case "pow" -> Math.pow(a, b);
            case "min" -> Math.min(a, b);
            case "max" -> Math.max(a, b);
            case "atan2" -> Math.atan2(a, b);
            case "mod" -> a - b * Math.floor(a / b);
            case "neg" -> -a;
            case "sin" -> Math.sin(a);
            case "cos" -> Math.cos(a);
            case "tan" -> Math.tan(a);
            case "sqrt" -> Math.sqrt(a);
            case "exp" -> Math.exp(a);
            case "abs" -> Math.abs(a);
            case "log" -> Math.log(a);
            case "asin" -> Math.asin(a);
            case "acos" -> Math.acos(a);
            case "atan" -> Math.atan(a);
            case "sign" -> Math.signum(a);
            case "floor" -> Math.floor(a);
            default -> throw ApiException.unprocessable("Phép toán số học không được hỗ trợ: " + op);
        };
        require(Double.isFinite(value), "Phương trình trả về giá trị không xác định hoặc vô hạn");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw ApiException.unprocessable(message);
    }
}
