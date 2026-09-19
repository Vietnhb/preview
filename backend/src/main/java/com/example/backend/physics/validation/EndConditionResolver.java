package com.example.backend.physics.validation;

import com.example.backend.physics.model.SolverOutput;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves declarative end conditions against solver output.  This class only
 * knows generic timeline capabilities; it has no knowledge of a lesson or a
 * physics model.
 */
public final class EndConditionResolver {
    public static final double MAX_DYNAMIC_SECONDS = 300.0;
    private static final double EPSILON = 1e-9;

    private EndConditionResolver() {
    }

    public record ResolvedEnd(double time, String reason, boolean conditionReached) {
    }

    public static JsonNode normalize(JsonNode specification, double fallbackDuration) {
        JsonNode explicit = specification == null ? null : specification.get("endCondition");
        if (explicit == null || explicit.isNull()) {
            explicit = specification == null ? null : specification.get("end_condition");
        }
        if (explicit != null && !explicit.isNull()) {
            if (!(explicit instanceof ObjectNode object)) return explicit.deepCopy();
            ObjectNode normalized = object.deepCopy();
            if (normalized.path("type").isTextual()) {
                normalized.put("type", normalized.path("type").asText().trim().toLowerCase(Locale.ROOT));
            }
            JsonNode event = normalized.get("event");
            if (event instanceof ObjectNode eventObject && eventObject.path("type").isTextual()) {
                eventObject.put("type", eventObject.path("type").asText().trim().toLowerCase(Locale.ROOT));
            }
            return normalized;
        }

        // Compatibility for old documents that stored duration directly.
        double duration = number(specification == null ? null : specification.get("duration"), fallbackDuration);
        ObjectNode legacy = JsonNodeFactory.instance.objectNode();
        legacy.put("type", "time_limit");
        legacy.put("duration", duration);
        return legacy;
    }

    public static List<String> validate(JsonNode specification, double fallbackDuration) {
        boolean explicitlyPresent = specification != null
                && ((specification.has("endCondition") && !specification.get("endCondition").isNull())
                || (specification.has("end_condition") && !specification.get("end_condition").isNull()));
        JsonNode condition = normalize(specification, fallbackDuration);
        List<String> errors = validateNode(condition, fallbackDuration);
        if (!explicitlyPresent && errors.isEmpty()) return List.of();
        return List.copyOf(errors);
    }

    public static List<String> validateNode(JsonNode condition, double fallbackDuration) {
        List<String> errors = new ArrayList<>();
        // A missing condition is a supported legacy document. It is
        // normalized to time_limit by normalize(...).
        if (condition == null || condition.isNull()) return List.of();
        if (condition == null || !condition.isObject()) {
            return List.of("endCondition must be an object");
        }
        String type = condition.path("type").asText("").trim().toLowerCase(Locale.ROOT);
        if (!List.of("time_limit", "threshold", "event", "cycle_count", "manual").contains(type)) {
            return List.of("Unsupported endCondition type: " + type);
        }
        switch (type) {
            case "time_limit" -> {
                double duration = condition.path("duration").asDouble(Double.NaN);
                if (!finitePositive(duration) || duration > MAX_DYNAMIC_SECONDS * 12) {
                    errors.add("endCondition.duration must be a finite positive number");
                }
            }
            case "threshold" -> {
                if (condition.path("quantity").asText("").isBlank()) errors.add("threshold.quantity is required");
                if (!List.of(">=", "<=", ">", "<", "==").contains(condition.path("operator").asText())) {
                    errors.add("threshold.operator is unsupported");
                }
                if (!finite(condition.path("value"))) errors.add("threshold.value must be finite");
                validateMaxTime(condition, errors);
            }
            case "event" -> {
                JsonNode event = condition.path("event");
                String eventType = event.path("type").asText("").trim().toLowerCase(Locale.ROOT);
                if (!List.of("contact", "collision").contains(eventType)) {
                    errors.add("event.type must be contact or collision");
                }
                if (!event.path("entities").isArray() || event.path("entities").isEmpty()
                        || !allText(event.path("entities"))) {
                    errors.add("event.entities must contain at least one entity id");
                }
                if ("contact".equals(eventType)) {
                    if (!event.has("quantity") && !event.has("operator") && !event.has("value")) {
                        errors.add("contact.event requires quantity, operator and value");
                    }
                    if (event.path("quantity").asText("").isBlank()) errors.add("contact.event.quantity is required when a contact threshold is provided");
                    if (!List.of(">=", "<=", ">", "<", "==").contains(event.path("operator").asText())) {
                        errors.add("contact.event.operator is unsupported");
                    }
                    if (!finite(event.get("value"))) errors.add("contact.event.value must be finite");
                }
                if ("collision".equals(eventType)
                        && (event.has("firstQuantity") || event.has("secondQuantity"))) {
                    if (event.path("firstQuantity").asText("").isBlank()
                            || event.path("secondQuantity").asText("").isBlank()) {
                        errors.add("collision.event.firstQuantity and secondQuantity must both be provided");
                    }
                }
                validateMaxTime(condition, errors);
            }
            case "cycle_count" -> {
                if (condition.path("quantity").asText("").isBlank()) errors.add("cycle_count.quantity is required");
                double count = condition.path("count").asDouble(Double.NaN);
                if (!finitePositive(count) || Math.rint(count) != count) errors.add("cycle_count.count must be a positive integer");
                validateMaxTime(condition, errors);
            }
            case "manual" -> validateMaxTime(condition, errors);
            default -> errors.add("Unsupported endCondition type: " + type);
        }
        return List.copyOf(errors);
    }

    public static double initialHorizon(JsonNode condition, double fallbackDuration) {
        String type = condition == null ? "time_limit" : condition.path("type").asText("time_limit");
        if (condition != null && "time_limit".equals(type) && finitePositive(condition.path("duration"))) {
            return condition.path("duration").asDouble();
        }
        if (condition != null && finitePositive(condition.path("maxTime"))) {
            return condition.path("maxTime").asDouble();
        }
        return Math.max(0.01, fallbackDuration);
    }

    public static boolean expandable(JsonNode condition, ResolvedEnd resolved, double horizon) {
        if (resolved.conditionReached() || horizon >= MAX_DYNAMIC_SECONDS - EPSILON || condition == null) return false;
        String type = condition.path("type").asText("");
        return !"time_limit".equals(type) && !"manual".equals(type) && !finitePositive(condition.path("maxTime"));
    }

    public static ResolvedEnd resolve(JsonNode condition, SolverOutput output) {
        List<Double> times = output == null || output.time() == null ? List.of() : output.time();
        double horizon = times.isEmpty() ? 0 : times.get(times.size() - 1);
        String type = condition == null ? "time_limit" : condition.path("type").asText("time_limit");
        double maxTime = finitePositive(condition == null ? null : condition.get("maxTime"))
                ? condition.get("maxTime").asDouble() : horizon;
        double limit = Math.min(horizon, maxTime);

        return switch (type) {
            case "time_limit" -> resolveTimeLimit(condition, horizon);
            case "threshold" -> resolvedOrMax(findThreshold(condition, output, limit), limit, "threshold");
            case "event" -> resolvedOrMax(findEvent(condition, output, limit), limit, "event");
            case "cycle_count" -> resolvedOrMax(findCycles(condition, output, limit), limit, "cycle_count");
            // A persisted run is bounded automatically. An interactive
            // manual stop can be represented by reason=manual later, but a
            // server-side horizon is always the safety boundary.
            case "manual" -> new ResolvedEnd(limit, "max_time", false);
            default -> new ResolvedEnd(limit, "max_time", false);
        };
    }

    private static ResolvedEnd resolveTimeLimit(JsonNode condition, double horizon) {
        double target = condition == null ? horizon : condition.path("duration").asDouble(horizon);
        if (target <= horizon + EPSILON) return new ResolvedEnd(Math.min(target, horizon), "time_limit", true);
        return new ResolvedEnd(horizon, "max_time", false);
    }

    private static ResolvedEnd resolvedOrMax(Double time, double limit, String reason) {
        return time == null
                ? new ResolvedEnd(Math.max(0, limit), "max_time", false)
                : new ResolvedEnd(Math.max(0, Math.min(time, limit)), reason, true);
    }

    /** Trims all timeline-aligned arrays and interpolates the final sample. */
    public static SolverOutput trim(SolverOutput output, double endTime) {
        if (output == null || output.time() == null || output.time().isEmpty()) return output;
        List<Double> sourceTimes = output.time();
        double target = Math.max(sourceTimes.get(0), Math.min(endTime, sourceTimes.get(sourceTimes.size() - 1)));
        int last = 0;
        while (last + 1 < sourceTimes.size() && sourceTimes.get(last + 1) <= target + EPSILON) last++;
        boolean append = sourceTimes.get(last) < target - EPSILON;
        List<Double> times = new ArrayList<>(sourceTimes.subList(0, last + 1));
        if (append) times.add(target);
        return new SolverOutput(times,
                trimGroup(output.positions(), sourceTimes, last, target, append),
                trimGroup(output.velocities(), sourceTimes, last, target, append),
                trimGroup(output.accelerations(), sourceTimes, last, target, append),
                trimGroup(output.values(), sourceTimes, last, target, append));
    }

    private static Map<String, List<Double>> trimGroup(Map<String, List<Double>> group, List<Double> times,
                                                        int last, double target, boolean append) {
        Map<String, List<Double>> result = new LinkedHashMap<>();
        if (group == null) return result;
        for (Map.Entry<String, List<Double>> entry : group.entrySet()) {
            List<Double> values = entry.getValue() == null ? List.of() : entry.getValue();
            if (values.size() != times.size()) {
                result.put(entry.getKey(), List.copyOf(values));
                continue;
            }
            List<Double> trimmed = new ArrayList<>(values.subList(0, last + 1));
            if (append) trimmed.add(interpolate(values, times, target));
            result.put(entry.getKey(), List.copyOf(trimmed));
        }
        return result;
    }

    private static Double findThreshold(JsonNode condition, SolverOutput output, double limit) {
        Series series = findSeries(output, condition.path("quantity").asText());
        if (series == null) return null;
        String operator = condition.path("operator").asText();
        double target = condition.path("value").asDouble();
        return crossing(series.values(), output.time(), limit, value -> matches(value, operator, target), target, operator);
    }

    private static Double findEvent(JsonNode condition, SolverOutput output, double limit) {
        JsonNode event = condition.path("event");
        String type = event.path("type").asText("").toLowerCase(Locale.ROOT);
        Double marker = eventMarker(output, type, limit);
        if (marker != null) return marker;
        if ("contact".equals(type)) {
            Series quantity = findSeries(output, event.path("quantity").asText());
            if (quantity == null || !finite(event.get("value"))) return null;
            String operator = event.path("operator").asText();
            if (!List.of(">=", "<=", ">", "<", "==").contains(operator)) return null;
            return contactCrossing(quantity.values(), output.time(), limit,
                    operator, event.path("value").asDouble());
        }

        String firstQuantity = event.path("firstQuantity").asText("");
        String secondQuantity = event.path("secondQuantity").asText("");
        if (!firstQuantity.isBlank() && !secondQuantity.isBlank()) {
            Series first = findSeries(output, firstQuantity);
            Series second = findSeries(output, secondQuantity);
            return first == null || second == null ? null
                    : differenceCrossing(first.values(), second.values(), output.time(), limit);
        }

        List<String> entities = new ArrayList<>();
        event.path("entities").forEach(entity -> {
            if (entity.isTextual() && !entity.asText().isBlank()) entities.add(entity.asText());
        });
        if (entities.size() >= 2) {
            Series first = findNamedSeries(output.positions(), entities.get(0));
            Series second = findNamedSeries(output.positions(), entities.get(1));
            if (first != null && second != null) {
                return differenceCrossing(first.values(), second.values(), output.time(), limit);
            }
        }
        return null;
    }

    private static Double contactCrossing(List<Double> values, List<Double> times, double limit,
                                          String operator, double target) {
        if (values == null || times == null || values.isEmpty() || values.size() != times.size()) return null;
        String boundaryOperator = switch (operator) {
            case "<" -> "<=";
            case ">" -> ">=";
            default -> operator;
        };
        int start = 0;
        if (times.get(0) <= limit + EPSILON && matches(values.get(0), boundaryOperator, target)) {
            // A body may launch from the contact surface. Ignore that initial
            // equality if the next sample moves away; detect its later return.
            if (Math.abs(values.get(0) - target) <= EPSILON && values.size() > 1
                    && !matches(values.get(1), boundaryOperator, target)) {
                start = 1;
            } else {
                return times.get(0);
            }
        }
        for (int i = Math.max(1, start + 1); i < values.size() && i < times.size(); i++) {
            if (times.get(i) > limit + EPSILON) break;
            double previous = values.get(i - 1);
            double current = values.get(i);
            boolean enteredContact = !matches(previous, boundaryOperator, target)
                    && matches(current, boundaryOperator, target);
            if (enteredContact || crosses(previous, current, target, boundaryOperator)) {
                return interpolate(previous, current, times.get(i - 1), times.get(i), target);
            }
        }
        return null;
    }

    private static Double eventMarker(SolverOutput output, String type, double limit) {
        if (output.values() == null) return null;
        for (Map.Entry<String, List<Double>> entry : output.values().entrySet()) {
            String key = compact(entry.getKey());
            if (!key.contains(type)) continue;
            List<Double> values = entry.getValue();
            if (values == null || values.isEmpty()) continue;
            if (values.size() == 1 && key.endsWith("time")
                    && values.get(0) > 0 && values.get(0) <= limit) return values.get(0);
            Double found = crossing(values, output.time(), limit, value -> value > 0.5, 0.5, ">");
            if (found != null) return found;
        }
        return null;
    }

    private static Double findCycles(JsonNode condition, SolverOutput output, double limit) {
        Series series = findSeries(output, condition.path("quantity").asText());
        if (series == null) return null;
        double count = condition.path("count").asDouble();
        double period = estimatePeriod(series.values(), output.time(), limit);
        if (!finitePositive(period)) return null;
        double target = period * count;
        return target <= limit + EPSILON ? target : null;
    }

    private static double estimatePeriod(List<Double> values, List<Double> times, double limit) {
        if (values == null || times == null || values.size() != times.size() || values.size() < 3) return Double.NaN;
        int end = 0;
        while (end + 1 < times.size() && times.get(end + 1) <= limit + EPSILON) end++;
        if (end < 2) return Double.NaN;
        double min = values.subList(0, end + 1).stream().filter(EndConditionResolver::finite).min(Comparator.naturalOrder()).orElse(Double.NaN);
        double max = values.subList(0, end + 1).stream().filter(EndConditionResolver::finite).max(Comparator.naturalOrder()).orElse(Double.NaN);
        if (!finite(min) || !finite(max) || max - min <= EPSILON) return Double.NaN;
        double mean = (min + max) / 2;
        List<Double> crossings = new ArrayList<>();
        for (int i = 1; i <= end; i++) {
            double before = values.get(i - 1) - mean;
            double after = values.get(i) - mean;
            if ((before < 0 && after >= 0) || (before > 0 && after <= 0)) {
                double ratio = (mean - values.get(i - 1)) / (values.get(i) - values.get(i - 1));
                crossings.add(times.get(i - 1) + ratio * (times.get(i) - times.get(i - 1)));
            }
        }
        if (crossings.size() >= 2) {
            List<Double> halfPeriods = new ArrayList<>();
            for (int i = 1; i < crossings.size(); i++) halfPeriods.add(crossings.get(i) - crossings.get(i - 1));
            return 2 * median(halfPeriods);
        }
        return Double.NaN;
    }

    private static Double crossing(List<Double> values, List<Double> times, double limit,
                                   java.util.function.DoublePredicate predicate, double target, String operator) {
        if (values == null || times == null || values.isEmpty() || values.size() != times.size()) return null;
        if (times.get(0) <= limit + EPSILON && predicate.test(values.get(0))) return times.get(0);
        for (int i = 1; i < values.size() && i < times.size(); i++) {
            if (times.get(i) > limit + EPSILON) break;
            if (predicate.test(values.get(i))) {
                double previous = values.get(i - 1);
                double current = values.get(i);
                if (crosses(previous, current, target, operator)) return interpolate(previous, current, times.get(i - 1), times.get(i), target);
                return times.get(i);
            }
            if (crosses(values.get(i - 1), values.get(i), target, operator)) {
                return interpolate(values.get(i - 1), values.get(i), times.get(i - 1), times.get(i), target);
            }
        }
        return null;
    }

    private static Double differenceCrossing(List<Double> first, List<Double> second, List<Double> times, double limit) {
        if (first == null || second == null || first.size() != second.size()) return null;
        List<Double> difference = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) difference.add(first.get(i) - second.get(i));
        return crossing(difference, times, limit, value -> Math.abs(value) <= EPSILON, 0, "==");
    }

    private static Series findSeries(SolverOutput output, String requested) {
        if (output == null || requested == null || requested.isBlank()) return null;
        String key = requested.trim();
        String normalized = key.toLowerCase(Locale.ROOT);
        String group = null;
        String name = normalized;
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            group = switch (normalized.substring(0, dot)) {
                case "position", "positions" -> "positions";
                case "velocity", "velocities" -> "velocities";
                case "acceleration", "accelerations" -> "accelerations";
                case "value", "values" -> "values";
                default -> normalized.substring(0, dot);
            };
            name = normalized.substring(dot + 1);
        }
        if (group != null) {
            Map<String, List<Double>> source = switch (group) {
                case "positions" -> output.positions();
                case "velocities" -> output.velocities();
                case "accelerations" -> output.accelerations();
                case "values" -> output.values();
                default -> Map.of();
            };
            return named(source, name);
        }
        Series value = named(output.values(), name);
        if (value != null) return value;
        value = named(output.positions(), name);
        if (value != null) return value;
        value = named(output.velocities(), name);
        if (value != null) return value;
        value = named(output.accelerations(), name);
        if (value != null) return value;

        // AI may use a semantic quantity name such as "oscillation" while
        // the solver exposes one unambiguous observable as values.x. Resolve
        // that alias by cardinality, never by a model id.
        List<Series> candidates = new ArrayList<>();
        addSeries(candidates, output.values());
        addSeries(candidates, output.positions());
        addSeries(candidates, output.velocities());
        addSeries(candidates, output.accelerations());
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static void addSeries(List<Series> target, Map<String, List<Double>> source) {
        if (source == null) return;
        source.forEach((key, values) -> {
            if (target.stream().noneMatch(existing -> existing.name().equalsIgnoreCase(key))) {
                target.add(new Series(key, values));
            }
        });
    }

    private static Series named(Map<String, List<Double>> values, String requested) {
        if (values == null) return null;
        return values.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(requested))
                .map(entry -> new Series(entry.getKey(), entry.getValue())).findFirst().orElse(null);
    }

    private static Series findNamedSeries(Map<String, List<Double>> values, String requested) {
        if (values == null) return null;
        return values.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(requested)
                        || compact(entry.getKey()).equals(compact(requested)))
                .map(entry -> new Series(entry.getKey(), entry.getValue())).findFirst().orElse(null);
    }

    private static boolean matches(double value, String operator, double target) {
        return switch (operator) {
            case ">=" -> value >= target;
            case "<=" -> value <= target;
            case ">" -> value > target;
            case "<" -> value < target;
            case "==" -> Math.abs(value - target) <= EPSILON;
            default -> false;
        };
    }

    private static boolean crosses(double before, double current, double target, String operator) {
        if (!finite(before) || !finite(current)) return false;
        return switch (operator) {
            case ">=", ">" -> before < target && current >= target;
            case "<=", "<" -> before > target && current <= target;
            case "==" -> (before - target) * (current - target) <= 0 && Math.abs(before - current) > EPSILON;
            default -> false;
        };
    }

    private static double interpolate(double before, double current, double beforeTime, double currentTime, double target) {
        if (Math.abs(current - before) <= EPSILON) return currentTime;
        double ratio = (target - before) / (current - before);
        return beforeTime + Math.max(0, Math.min(1, ratio)) * (currentTime - beforeTime);
    }

    private static double interpolate(List<Double> values, List<Double> times, double target) {
        int last = times.size() - 1;
        for (int i = 1; i <= last; i++) {
            if (target <= times.get(i)) return interpolateAtTime(values.get(i - 1), values.get(i),
                    times.get(i - 1), times.get(i), target);
        }
        return values.get(last);
    }

    private static double interpolateAtTime(double before, double current, double beforeTime,
                                             double currentTime, double targetTime) {
        if (Math.abs(currentTime - beforeTime) <= EPSILON) return current;
        double ratio = (targetTime - beforeTime) / (currentTime - beforeTime);
        return before + Math.max(0, Math.min(1, ratio)) * (current - before);
    }

    private static void validateMaxTime(JsonNode condition, List<String> errors) {
        if (condition.has("maxTime") && !condition.get("maxTime").isNull()
                && (!finitePositive(condition.get("maxTime")) || condition.get("maxTime").asDouble() > MAX_DYNAMIC_SECONDS)) {
            errors.add("endCondition.maxTime must be finite, positive and <= " + MAX_DYNAMIC_SECONDS);
        }
    }

    private static boolean allText(JsonNode values) {
        for (JsonNode value : values) if (!value.isTextual() || value.asText().isBlank()) return false;
        return true;
    }

    private static boolean finite(JsonNode node) { return node != null && node.isNumber() && finite(node.asDouble()); }
    private static boolean finitePositive(JsonNode node) { return finite(node) && node.asDouble() > 0; }
    private static boolean finite(double value) { return Double.isFinite(value); }
    private static boolean finitePositive(double value) { return finite(value) && value > 0; }
    private static double number(JsonNode node, double fallback) { return finite(node) ? node.asDouble() : Math.max(0.01, fallback); }
    private static String compact(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = values.stream().filter(EndConditionResolver::finite).sorted().toList();
        if (sorted.isEmpty()) return Double.NaN;
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }

    private record Series(String name, List<Double> values) { }
}
