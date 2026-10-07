package com.example.backend.system.physics.service;

import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Interprets bounded arithmetic ASTs from approved schemas; no scene or topic-specific branches. */
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
                unitDimensions.put(unit.path("canonical").asText(), dimension);
                if (unit.path("factor").asDouble() == 1) for (JsonNode alias : unit.path("aliases")) unitDimensions.put(alias.asText(), dimension);
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException("Unit dimensions are unavailable", ex); }
    }

    private static final int MIN_STEPS = 200;
    private static final int MAX_BASE_STEPS = 1000;

    private record Bound(String id, JsonNode capability, Map<String, Double> inputs) { }

    public ObjectNode compute(JsonNode definition, JsonNode spec, JsonNode overrides) {
        Map<String, Double> parameters = new LinkedHashMap<>();
        for (JsonNode parameter : spec.path("parameters")) {
            String name = parameter.path("name").asText();
            double value = overrides.has(name) ? overrides.path(name).asDouble(Double.NaN)
                    : parameter.path("value").asDouble(Double.NaN);
            require(!name.isBlank() && !parameters.containsKey(name) && Double.isFinite(value), "Tham số không hợp lệ");
            require(value >= parameter.path("min").asDouble(-Double.MAX_VALUE)
                    && value <= parameter.path("max").asDouble(Double.MAX_VALUE), "Tham số nằm ngoài phạm vi cho phép: " + name);
            parameters.put(name, value);
        }
        overrides.fieldNames().forEachRemaining(name -> require(parameters.containsKey(name), "Tham số không xác định: " + name));
        double duration = spec.path("durationSeconds").asDouble(Double.NaN);
        if (spec.hasNonNull("durationParameter")) {
            String key = spec.path("durationParameter").asText();
            require(parameters.containsKey(key), "Tham số thời gian không xác định");
            for (JsonNode parameter : spec.path("parameters")) if (parameter.path("name").asText().equals(key)) {
                require(parameter.path("unit").asText().equals("s") && parameter.path("value").asDouble() == duration,
                        "Tham số thời gian phải khớp thời lượng ban đầu và dùng đơn vị giây");
            }
            duration = parameters.get(key);
        }
        require(Double.isFinite(duration) && duration > 0 && duration <= maxDuration, "Thời lượng mô phỏng không hợp lệ");
        List<Bound> bindings = new ArrayList<>();
        for (JsonNode model : spec.path("physicsModels")) {
            require(bindings.size() < maxParticipants, "Số lượng vật thể vượt quá giới hạn cho phép");
            String id = model.path("id").asText();
            require(!id.isBlank() && bindings.stream().noneMatch(b -> b.id.equals(id)), "Định danh mô hình bị trùng hoặc bị thiếu");
            JsonNode capability = null;
            for (JsonNode candidate : definition.path("capabilities")) {
                if (candidate.path("capabilityId").asText().equals(model.path("capabilityId").asText())) capability = candidate;
            }
            require(capability != null && capability.path("execution").path("math").isObject(),
                    "Chức năng chưa có hệ phương trình thực thi được phê duyệt");
            checkDimensions(capability);
            Map<String, Double> inputs = new LinkedHashMap<>();
            for (JsonNode input : capability.path("canonicalInputs")) {
                String key = input.path("key").asText();
                JsonNode binding = model.path("inputs").path(key);
                double value;
                if (binding.isTextual()) {
                    require(parameters.containsKey(binding.asText()), "Tham số đầu vào không xác định: " + binding.asText());
                    value = parameters.get(binding.asText());
                    for (JsonNode parameter : spec.path("parameters")) {
                        if (parameter.path("name").asText().equals(binding.asText()))
                            require(parameter.path("unit").asText().equals(input.path("unit").asText()), "Đầu vào phải sử dụng đơn vị SI chuẩn: " + key);
                    }
                } else value = binding.isNumber() ? binding.asDouble() : input.path("defaultValue").asDouble(Double.NaN);
                require(Double.isFinite(value), "Thiếu đầu vào chuẩn: " + key);
                require(value >= input.path("min").asDouble(-Double.MAX_VALUE)
                        && value <= input.path("max").asDouble(Double.MAX_VALUE), "Giá trị nằm ngoài miền tính toán của bộ giải: " + key);
                inputs.put(key, value);
            }
            bindings.add(new Bound(id, capability, inputs));
        }
        // Physical time scales range from milliseconds (AC, RC) to years (decay, orbits).
        // Start from a presentation-friendly resolution and refine (h -> h/2) only while
        // the reference / refinement checks fail, within the configured sample budget.
        int budgetSteps = (int) Math.max(0, (maxSamples / Math.max(1, bindings.size()) - 1) / 2);
        int steps = (int) Math.max(Math.min(MIN_STEPS, budgetSteps),
                Math.min(Math.min((double) budgetSteps, MAX_BASE_STEPS), Math.ceil(duration / defaultStep)));
        require(steps > 0 && (long) (steps * 2 + 1) * Math.max(1, bindings.size()) <= maxSamples,
                "Dữ liệu diễn tiến vượt quá giới hạn số mẫu. Vui lòng giảm thời lượng mô phỏng");
        ObjectNode result = run(spec, bindings, duration, steps);
        while ("FLAGGED".equals(result.path("validation").path("status").asText()) && steps * 2L <= budgetSteps) {
            steps *= 2;
            result = run(spec, bindings, duration, steps);
        }
        return result;
    }

    private ObjectNode run(JsonNode spec, List<Bound> bindings, double duration, int steps) {
        ObjectNode result = json.createObjectNode();
        ObjectNode validation = result.putObject("validation");
        var flags = validation.putArray("flags");
        var assumptions = validation.putArray("assumptions");
        var evidence = validation.putArray("convergenceEvidence");
        ObjectNode invariants = validation.putObject("invariantResults");
        validation.put("solverVersion", "schema-ast-rk4/1.0");
        validation.put("formulaSource", "APPROVED_TOPIC_SCHEMA");
        validation.put("verificationScope", "Các đại lượng chuẩn đã được liên kết; các giả định vẫn được hiển thị để kiểm tra");
        ObjectNode timeline = result.putObject("solverTimeline");
        timeline.put("durationSeconds", duration);
        var frames = timeline.putArray("frames");
        List<List<Map<String, Double>>> trajectories = new ArrayList<>();
        boolean allReference = !bindings.isEmpty();
        boolean numericalEvidence = !bindings.isEmpty();
        boolean passed = true;
        double absoluteError = 0;
        double relativeError = 0;
        double maxResidual = 0;
        int benchmarksChecked = 0;
        for (Bound bound : bindings) {
            JsonNode math = bound.capability.path("execution").path("math");
            List<Map<String, Double>> coarse = integrate(bound, duration, steps);
            List<Map<String, Double>> fine = integrate(bound, duration, steps * 2);
            trajectories.add(coarse);
            boolean hasReference = math.path("closedForm").isObject() && !math.path("closedForm").isEmpty();
            allReference &= hasReference;
            double absTolerance = bound.capability.path("validation").path("absoluteTolerance").asDouble(Double.NaN);
            double relTolerance = bound.capability.path("validation").path("relativeTolerance").asDouble(Double.NaN);
            require(Double.isFinite(absTolerance) && absTolerance >= 0 && Double.isFinite(relTolerance) && relTolerance >= 0,
                    "Mô hình cần có sai số cho phép để kiểm định");
            Map<String, Double> initialInvariants = new LinkedHashMap<>();
            for (int i = 0; i <= steps; i++) {
                Map<String, Double> sample = coarse.get(i);
                Map<String, Double> reference = hasReference ? evaluateMap(math.path("closedForm"), bound.inputs,
                        Map.of("t", duration * i / steps)) : fine.get(i * 2);
                for (String key : sample.keySet()) {
                    require(reference.containsKey(key), "Dữ liệu đối chiếu thiếu đầu ra chuẩn: " + key);
                    double error = Math.abs(sample.get(key) - reference.get(key));
                    double scale = Math.max(Math.abs(sample.get(key)), Math.abs(reference.get(key)));
                    absoluteError = Math.max(absoluteError, error);
                    relativeError = Math.max(relativeError, error / Math.max(scale, absTolerance > 0 ? absTolerance : Double.MIN_NORMAL));
                    passed &= error <= absTolerance + relTolerance * scale;
                }
                Map<String, Double> inv = evaluateMap(math.path("invariants"), bound.inputs, sample);
                if (i == 0) initialInvariants.putAll(inv);
                for (var invariant : inv.entrySet()) {
                    double baseline = initialInvariants.get(invariant.getKey());
                    boolean valid = Math.abs(invariant.getValue() - baseline) <= absTolerance + relTolerance * Math.abs(baseline);
                    String name = bound.id + "." + invariant.getKey();
                    invariants.put(name, invariants.path(name).asBoolean(true) && valid);
                    passed &= valid;
                }
                if (i >= 2 && i <= steps - 2) {
                    Map<String, Double> rates = evaluateMap(math.path("rates"), bound.inputs, withTime(sample, duration * i / steps));
                    for (var rate : rates.entrySet()) {
                        String key = rate.getKey();
                        require(sample.containsKey(key), "Kết quả bộ giải thiếu trạng thái phương trình vi phân: " + key);
                        double derivative = (-coarse.get(i + 2).get(key) + 8 * coarse.get(i + 1).get(key)
                                - 8 * coarse.get(i - 1).get(key) + coarse.get(i - 2).get(key)) / (12 * duration / steps);
                        double residual = Math.abs(derivative - rate.getValue());
                        maxResidual = Math.max(maxResidual, residual);
                        passed &= residual <= absTolerance + relTolerance * Math.abs(rate.getValue());
                    }
                }
            }
            int declaredBenchmarks = 0;
            for (JsonNode benchmark : bound.capability.path("validation").path("benchmarks")) {
                if (!benchmark.path("expectedOutputs").isObject() || benchmark.path("expectedOutputs").isEmpty()) continue;
                Map<String, Double> benchmarkInputs = new LinkedHashMap<>(bound.inputs);
                benchmark.path("inputs").fields().forEachRemaining(field -> benchmarkInputs.put(field.getKey(), field.getValue().asDouble()));
                double benchmarkDuration = benchmark.path("durationSeconds").asDouble(1);
                require(benchmarkDuration > 0 && benchmarkDuration <= maxDuration, "Thời lượng kiểm chuẩn không hợp lệ");
                int benchmarkSteps = (int) Math.ceil(benchmarkDuration / defaultStep);
                require(benchmarkSteps <= maxSamples, "Số mẫu kiểm chuẩn vượt quá giới hạn");
                var benchmarkSamples = integrate(new Bound(bound.id, bound.capability, benchmarkInputs), benchmarkDuration, benchmarkSteps);
                Map<String, Double> last = benchmarkSamples.get(benchmarkSamples.size() - 1);
                var expected = benchmark.path("expectedOutputs").fields();
                while (expected.hasNext()) {
                    var field = expected.next();
                    require(last.containsKey(field.getKey()), "Bài kiểm chuẩn tham chiếu đến đầu ra không xác định");
                    double value = field.getValue().asDouble(Double.NaN);
                    passed &= Double.isFinite(value) && Math.abs(last.get(field.getKey()) - value) <= absTolerance + relTolerance * Math.abs(value);
                }
                declaredBenchmarks++; benchmarksChecked++;
            }
            boolean hasInvariants = math.path("invariants").isObject() && !math.path("invariants").isEmpty();
            numericalEvidence &= hasReference || (hasInvariants && declaredBenchmarks > 0 && steps >= 4);
            evidence.add(bound.id + ": compared " + (steps + 1) + " checkpoints using "
                    + (hasReference ? "independent approved closed-form AST" : "step refinement h -> h/2 and declared invariants"));
            for (JsonNode assumption : bound.capability.path("assumptions")) assumptions.add(assumption.asText());
        }
        for (int i = 0; i <= steps; i++) {
            ObjectNode frame = frames.addObject();
            frame.put("t", duration * i / steps);
            ObjectNode fields = frame.putObject("values");
            fields.put("t", duration * i / steps);
            for (int b = 0; b < bindings.size(); b++) {
                String prefix = bindings.get(b).id + ".";
                trajectories.get(b).get(i).forEach((key, value) -> fields.put(prefix + key, value));
            }
        }
        boolean coverage = spec.path("physicsCoverage").asText().equals("COMPLETE");
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

    private Map<String, Double> dimension(JsonNode ast, Map<String, Map<String, Double>> symbols, int depth, int[] nodes) {
        require(depth <= maxDepth && ++nodes[0] <= maxNodes, "Phép kiểm tra thứ nguyên vượt quá giới hạn xử lý");
        if (ast.isNumber()) return ast.asDouble() == 0 ? null : Map.of();
        if (ast.isTextual()) { require(symbols.containsKey(ast.asText()), "Thứ nguyên đại lượng chưa được khai báo: " + ast.asText()); return symbols.get(ast.asText()); }
        require(ast.isArray() && ast.size() >= 2 && ast.size() <= 3, "Cấu trúc phương trình không hợp lệ");
        String op = ast.path(0).asText();
        Map<String, Double> a = dimension(ast.get(1), symbols, depth + 1, nodes);
        Map<String, Double> b = ast.size() == 3 ? dimension(ast.get(2), symbols, depth + 1, nodes) : Map.of();
        return switch (op) {
            case "add", "sub" -> { require(a == null || b == null || a.equals(b), "Không thể cộng các đại lượng khác thứ nguyên"); yield a == null ? b : a; }
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
            case "sin", "cos", "exp", "log", "asin", "acos", "atan" -> {
                require(a == null || a.isEmpty(), "Đầu vào hàm siêu việt phải không có thứ nguyên; góc dùng đơn vị radian"); yield Map.of(); }
            default -> throw ApiException.unprocessable("Phép toán thứ nguyên không xác định: " + op);
        };
    }

    private Map<String, Double> combine(Map<String, Double> a, Map<String, Double> b, double sign) {
        Map<String, Double> result = new LinkedHashMap<>(a);
        b.forEach((key, value) -> result.merge(key, sign * value, Double::sum));
        result.entrySet().removeIf(entry -> entry.getValue() == 0);
        return result;
    }

    private List<Map<String, Double>> integrate(Bound bound, double duration, int steps) {
        JsonNode math = bound.capability.path("execution").path("math");
        Map<String, Double> state = evaluateMap(math.path("initial"), bound.inputs, Map.of("t", 0.0));
        require(!state.isEmpty(), "Hệ phương trình chưa có trạng thái ban đầu");
        List<Map<String, Double>> samples = new ArrayList<>();
        double h = duration / steps;
        for (int i = 0; i <= steps; i++) {
            double t = duration * i / steps;
            samples.add(evaluateMap(math.path("outputs"), bound.inputs, withTime(state, t)));
            if (i == steps) break;
            Map<String, Double> k1 = evaluateMap(math.path("rates"), bound.inputs, withTime(state, t));
            Map<String, Double> k2 = evaluateMap(math.path("rates"), bound.inputs, withTime(advance(state, k1, h / 2), t + h / 2));
            Map<String, Double> k3 = evaluateMap(math.path("rates"), bound.inputs, withTime(advance(state, k2, h / 2), t + h / 2));
            Map<String, Double> k4 = evaluateMap(math.path("rates"), bound.inputs, withTime(advance(state, k3, h), t + h));
            Map<String, Double> next = new LinkedHashMap<>();
            for (String key : state.keySet()) {
                require(k1.containsKey(key) && k2.containsKey(key) && k3.containsKey(key) && k4.containsKey(key), "Thiếu đạo hàm của trạng thái: " + key);
                double value = state.get(key) + h / 6 * (k1.get(key) + 2 * k2.get(key) + 2 * k3.get(key) + k4.get(key));
                require(Double.isFinite(value), "Bộ giải trả về giá trị không xác định hoặc vô hạn");
                next.put(key, value);
            }
            state = next;
        }
        return samples;
    }

    private Map<String, Double> withTime(Map<String, Double> state, double t) {
        Map<String, Double> result = new LinkedHashMap<>(state); result.put("t", t); return result;
    }

    private Map<String, Double> advance(Map<String, Double> state, Map<String, Double> rates, double h) {
        Map<String, Double> result = new LinkedHashMap<>();
        state.forEach((key, value) -> { require(rates.containsKey(key), "Thiếu đạo hàm của trạng thái: " + key); result.put(key, value + h * rates.get(key)); });
        return result;
    }

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
        require(ast.isArray() && ast.size() >= 2 && ast.size() <= 3, "Cấu trúc biểu thức số học không hợp lệ");
        String op = ast.path(0).asText();
        boolean unary = List.of("neg", "sin", "cos", "sqrt", "exp", "abs", "log", "asin", "acos", "atan").contains(op);
        require(ast.size() == (unary ? 2 : 3), "Số toán hạng trong biểu thức không hợp lệ");
        double a = evaluate(ast.get(1), lookup, depth + 1, nodes);
        double b = ast.size() > 2 ? evaluate(ast.get(2), lookup, depth + 1, nodes) : 0;
        double value = switch (op) {
            case "add" -> a + b;
            case "sub" -> a - b;
            case "mul" -> a * b;
            case "div" -> a / b;
            case "pow" -> Math.pow(a, b);
            case "neg" -> -a;
            case "sin" -> Math.sin(a);
            case "cos" -> Math.cos(a);
            case "sqrt" -> Math.sqrt(a);
            case "exp" -> Math.exp(a);
            case "abs" -> Math.abs(a);
            case "log" -> Math.log(a);
            case "asin" -> Math.asin(a);
            case "acos" -> Math.acos(a);
            case "atan" -> Math.atan(a);
            default -> throw ApiException.unprocessable("Phép toán số học không được hỗ trợ: " + op);
        };
        require(Double.isFinite(value), "Phương trình trả về giá trị không xác định hoặc vô hạn");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw ApiException.unprocessable(message);
    }
}
