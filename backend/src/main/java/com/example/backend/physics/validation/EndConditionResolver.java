package com.example.backend.physics.validation;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.ScalarField;

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
 * Resolves declarative end conditions against solver output. This class only
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

    private static final EndConditionStrategyRegistry STRATEGIES = new EndConditionStrategyRegistry(List.of(
            strategy(EndConditionType.TIME_LIMIT, EndConditionContract.TimeLimit.class,
                    (condition, output, limit) -> resolveTimeLimit(condition, outputHorizon(output))),
            strategy(EndConditionType.THRESHOLD, EndConditionContract.Threshold.class,
                    (condition, output, limit) -> resolvedOrMax(findThreshold(condition, output, limit), limit, "threshold")),
            strategy(EndConditionType.EVENT, EndConditionContract.Event.class,
                    (condition, output, limit) -> resolvedOrMax(findEvent(condition, output, limit), limit, "event")),
            strategy(EndConditionType.CYCLE_COUNT, EndConditionContract.CycleCount.class,
                    (condition, output, limit) -> resolvedOrMax(findCycles(condition, output, limit), limit, "cycle_count")),
            strategy(EndConditionType.MANUAL, EndConditionContract.Manual.class,
                    (condition, output, limit) -> new ResolvedEnd(limit, "max_time", false))));

    @FunctionalInterface
    private interface TypedResolution<T extends EndConditionContract> {
        ResolvedEnd resolve(T condition, SolverOutput output, double limit);
    }

    private static <T extends EndConditionContract> EndConditionStrategy<T> strategy(EndConditionType type,
            Class<T> contractType, TypedResolution<T> resolution) {
        return new EndConditionStrategy<>() {
            @Override public EndConditionType type() { return type; }
            @Override public Class<T> contractType() { return contractType; }
            @Override public ResolvedEnd resolve(T condition, SolverOutput output, double limit) {
                return resolution.resolve(condition, output, limit);
            }
        };
    }

    public static JsonNode normalize(JsonNode specification, double fallbackDuration) {
        JsonNode explicit = specification == null ? null : specification.get("endCondition");
        if (explicit == null || explicit.isNull()) {
            explicit = specification == null ? null : specification.get("end_condition");
        }
        if (explicit != null && !explicit.isNull()) {
            if (!(explicit instanceof ObjectNode object))
                return explicit.deepCopy();
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
        if (!explicitlyPresent && errors.isEmpty())
            return List.of();
        return List.copyOf(errors);
    }

    public static List<String> validateNode(JsonNode condition, double fallbackDuration) {
        List<String> errors = new ArrayList<>();
        // A missing condition is a supported legacy document. It is
        // normalized to time_limit by normalize(...).
        if (condition == null || condition.isNull())
            return List.of();
        if (condition == null || !condition.isObject()) {
            return List.of("endCondition must be an object");
        }
        String rawType = condition.path("type").asText("");
        EndConditionType type = EndConditionType.fromWireName(rawType);
        if (type == null) {
            return List.of("Unsupported endCondition type: " + rawType.trim().toLowerCase(Locale.ROOT));
        }
        switch (type) {
            case TIME_LIMIT -> {
                double duration = condition.path("duration").asDouble(Double.NaN);
                if (!finitePositive(duration) || duration > MAX_DYNAMIC_SECONDS * 12) {
                    errors.add("endCondition.duration must be a finite positive number");
                }
            }
            case THRESHOLD -> {
                if (condition.path("quantity").asText("").isBlank())
                    errors.add("threshold.quantity is required");
                if (ComparisonOperator.parse(condition.path("operator").asText()) == null) {
                    errors.add("threshold.operator is unsupported");
                }
                if (!finite(condition.path("value")))
                    errors.add("threshold.value must be finite");
                validateMaxTime(condition, errors);
            }
            case EVENT -> {
                JsonNode event = condition.path("event");
                String rawEventType = event.path("type").asText("").trim().toLowerCase(Locale.ROOT);
                EndConditionContract.EventKind eventType = switch (rawEventType) {
                    case "contact" -> EndConditionContract.EventKind.CONTACT;
                    case "collision" -> EndConditionContract.EventKind.COLLISION;
                    default -> null;
                };
                if (eventType == null) {
                    errors.add("event.type must be contact or collision");
                }
                if (!event.path("entities").isArray() || event.path("entities").isEmpty()
                        || !allText(event.path("entities"))) {
                    errors.add("event.entities must contain at least one entity id");
                }
                if (eventType == EndConditionContract.EventKind.CONTACT) {
                    if (!event.has("quantity") && !event.has("operator") && !event.has("value")) {
                        errors.add("contact.event requires quantity, operator and value");
                    }
                    if (event.path("quantity").asText("").isBlank())
                        errors.add("contact.event.quantity is required when a contact threshold is provided");
                    if (ComparisonOperator.parse(event.path("operator").asText()) == null) {
                        errors.add("contact.event.operator is unsupported");
                    }
                    if (!finite(event.get("value")))
                        errors.add("contact.event.value must be finite");
                }
                if (eventType == EndConditionContract.EventKind.COLLISION
                        && (event.has("firstQuantity") || event.has("secondQuantity"))) {
                    if (event.path("firstQuantity").asText("").isBlank()
                            || event.path("secondQuantity").asText("").isBlank()) {
                        errors.add("collision.event.firstQuantity and secondQuantity must both be provided");
                    }
                }
                validateMaxTime(condition, errors);
            }
            case CYCLE_COUNT -> {
                if (condition.path("quantity").asText("").isBlank())
                    errors.add("cycle_count.quantity is required");
                double count = condition.path("count").asDouble(Double.NaN);
                if (!finitePositive(count) || Math.rint(count) != count || !condition.path("count").canConvertToInt())
                    errors.add("cycle_count.count must be a positive 32-bit integer");
                validateMaxTime(condition, errors);
            }
            case MANUAL -> validateMaxTime(condition, errors);
        }
        return List.copyOf(errors);
    }

    public static double initialHorizon(JsonNode condition, double fallbackDuration) {
        try {
            return initialHorizon(LegacyEndConditionJsonAdapterV1.compile(condition, fallbackDuration), fallbackDuration);
        } catch (IllegalArgumentException invalidLegacyCondition) {
            return Math.max(0.01, fallbackDuration);
        }
    }

    public static double initialHorizon(EndConditionContract condition, double fallbackDuration) {
        if (condition == null) throw new IllegalArgumentException("Compiled end condition is required");
        if (condition instanceof EndConditionContract.TimeLimit timeLimit) return timeLimit.duration();
        if (condition.maxTime() != null) return condition.maxTime();
        return Math.max(0.01, fallbackDuration);
    }

    public static boolean expandable(JsonNode condition, ResolvedEnd resolved, double horizon) {
        if (condition == null) return false;
        try {
            return expandable(LegacyEndConditionJsonAdapterV1.compile(condition, horizon), resolved, horizon);
        } catch (IllegalArgumentException invalidLegacyCondition) {
            return false;
        }
    }

    public static boolean expandable(EndConditionContract condition, ResolvedEnd resolved, double horizon) {
        if (resolved.conditionReached() || horizon >= MAX_DYNAMIC_SECONDS - EPSILON) return false;
        return condition.type() != EndConditionType.TIME_LIMIT && condition.type() != EndConditionType.MANUAL
                && condition.maxTime() == null;
    }

    public static ResolvedEnd resolve(JsonNode condition, SolverOutput output) {
        double horizon = outputHorizon(output);
        return resolve(LegacyEndConditionJsonAdapterV1.compile(condition, horizon), output);
    }

    public static ResolvedEnd resolve(EndConditionContract condition, SolverOutput output) {
        if (condition == null) throw new IllegalArgumentException("Compiled end condition is required");
        double horizon = outputHorizon(output);
        double limit = condition.maxTime() == null ? horizon : Math.min(horizon, condition.maxTime());
        return STRATEGIES.resolve(condition, output, limit);
    }

    private static double outputHorizon(SolverOutput output) {
        return output == null || output.time() == null || output.time().isEmpty()
                ? 0 : output.time().get(output.time().size() - 1);
    }

    private static ResolvedEnd resolveTimeLimit(EndConditionContract.TimeLimit condition, double horizon) {
        double target = condition.duration();
        if (target <= horizon + EPSILON)
            return new ResolvedEnd(Math.min(target, horizon), "time_limit", true);
        return new ResolvedEnd(horizon, "max_time", false);
    }

    private static ResolvedEnd resolvedOrMax(Double time, double limit, String reason) {
        return time == null
                ? new ResolvedEnd(Math.max(0, limit), "max_time", false)
                : new ResolvedEnd(Math.max(0, Math.min(time, limit)), reason, true);
    }

    /** Trims all timeline-aligned arrays and interpolates the final sample. */
    public static SolverOutput trim(SolverOutput output, double endTime) {
        if (output == null || output.time() == null || output.time().isEmpty())
            return output;
        List<Double> sourceTimes = output.time();
        double target = Math.max(sourceTimes.get(0), Math.min(endTime, sourceTimes.get(sourceTimes.size() - 1)));
        int last = 0;
        while (last + 1 < sourceTimes.size() && sourceTimes.get(last + 1) <= target + EPSILON)
            last++;
        boolean append = sourceTimes.get(last) < target - EPSILON;
        List<Double> times = new ArrayList<>(sourceTimes.subList(0, last + 1));
        if (append)
            times.add(target);
        return new SolverOutput(times,
                trimGroup(output.positions(), sourceTimes, last, target, append),
                trimGroup(output.velocities(), sourceTimes, last, target, append),
                trimGroup(output.accelerations(), sourceTimes, last, target, append),
                trimGroup(output.values(), sourceTimes, last, target, append),
                trimFields(output.scalarFields(), endTime), output.scalarOutputs());
    }

    private static Map<String, List<Double>> trimGroup(Map<String, List<Double>> group, List<Double> times,
            int last, double target, boolean append) {
        Map<String, List<Double>> result = new LinkedHashMap<>();
        if (group == null)
            return result;
        for (Map.Entry<String, List<Double>> entry : group.entrySet()) {
            List<Double> values = entry.getValue() == null ? List.of() : entry.getValue();
            if (values.size() != times.size()) {
                result.put(entry.getKey(), List.copyOf(values));
                continue;
            }
            List<Double> trimmed = new ArrayList<>(values.subList(0, last + 1));
            if (append)
                trimmed.add(interpolate(values, times, target));
            result.put(entry.getKey(), List.copyOf(trimmed));
        }
        return result;
    }

    private static Map<String, ScalarField> trimFields(Map<String, ScalarField> fields, double endTime) {
        if (fields == null || fields.isEmpty()) return Map.of();
        Map<String, ScalarField> result = new LinkedHashMap<>();
        fields.forEach((key, field) -> result.put(key, trimField(field, endTime)));
        return result;
    }

    private static ScalarField trimField(ScalarField field, double endTime) {
        List<Double> sourceTimes = field.time();
        double target = Math.max(sourceTimes.getFirst(), Math.min(endTime, sourceTimes.getLast()));
        int last = 0;
        while (last + 1 < sourceTimes.size() && sourceTimes.get(last + 1) <= target + EPSILON) last++;
        boolean append = sourceTimes.get(last) < target - EPSILON;
        List<Double> times = new ArrayList<>(sourceTimes.subList(0, last + 1));
        List<List<Double>> values = new ArrayList<>(field.values().subList(0, last + 1));
        if (append) {
            times.add(target);
            List<Double> before = field.values().get(last);
            List<Double> after = field.values().get(Math.min(last + 1, field.values().size() - 1));
            double ratio = (target - sourceTimes.get(last))
                    / (sourceTimes.get(Math.min(last + 1, sourceTimes.size() - 1)) - sourceTimes.get(last));
            List<Double> interpolated = new ArrayList<>(before.size());
            for (int i = 0; i < before.size(); i++) interpolated.add(before.get(i) + ratio * (after.get(i) - before.get(i)));
            values.add(interpolated);
        }
        List<Integer> shape = new ArrayList<>(field.shape());
        shape.set(0, times.size());
        return new ScalarField(field.version(), field.type(), field.physicalDimension(), field.axes(), shape,
                times, values, field.valueUnit(), field.timeUnit(), field.sampling(), field.interpolation(), field.boundary());
    }

    private static Double findThreshold(EndConditionContract.Threshold condition, SolverOutput output, double limit) {
        Series series = findSeries(output, condition.source());
        if (series == null)
            return null;
        double target = condition.value();
        return crossing(series.values(), output.time(), limit, value -> matches(value, condition.operator(), target), target,
                condition.operator());
    }

    private static Double findEvent(EndConditionContract.Event event, SolverOutput output, double limit) {
        // Event markers are schema-bound. Never infer them from a substring of
        // an arbitrary output key; the event must name the declared series.
        Double marker = event.markerSource() == null ? null : eventMarker(output, event.markerSource(), limit);
        if (marker != null)
            return marker;
        if (event.kind() == EndConditionContract.EventKind.CONTACT) {
            Series quantity = findSeries(output, event.source());
            if (quantity == null || event.value() == null)
                return null;
            return contactCrossing(quantity.values(), output.time(), limit,
                    event.operator(), event.value());
        }

        if (event.firstSource() != null && event.secondSource() != null) {
            Series first = findSeries(output, event.firstSource());
            Series second = findSeries(output, event.secondSource());
            return first == null || second == null ? null
                    : differenceCrossing(first.values(), second.values(), output.time(), limit);
        }

        return null;
    }

    private static Double contactCrossing(List<Double> values, List<Double> times, double limit,
            ComparisonOperator operator, double target) {
        if (values == null || times == null || values.isEmpty() || values.size() != times.size())
            return null;
        ComparisonOperator boundaryOperator = operator.boundary();
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
            if (times.get(i) > limit + EPSILON)
                break;
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

    private static Double eventMarker(SolverOutput output, OutputSourceBinding markerSource, double limit) {
        Series marker = findSeries(output, markerSource);
        if (marker == null || marker.values() == null || marker.values().isEmpty()) return null;
        if (marker.values().size() == 1 && marker.values().get(0) > 0 && marker.values().get(0) <= limit) {
            return marker.values().get(0);
        }
        return crossing(marker.values(), output.time(), limit, value -> value > 0.5, 0.5,
                ComparisonOperator.GREATER);
    }

    private static Double findCycles(EndConditionContract.CycleCount condition, SolverOutput output, double limit) {
        Series series = findSeries(output, condition.source());
        if (series == null)
            return null;
        double period = estimatePeriod(series.values(), output.time(), limit);
        if (!finitePositive(period))
            return null;
        double target = period * condition.count();
        return target <= limit + EPSILON ? target : null;
    }

    private static double estimatePeriod(List<Double> values, List<Double> times, double limit) {
        if (values == null || times == null || values.size() != times.size() || values.size() < 3)
            return Double.NaN;
        int end = 0;
        while (end + 1 < times.size() && times.get(end + 1) <= limit + EPSILON)
            end++;
        if (end < 2)
            return Double.NaN;
        double min = values.subList(0, end + 1).stream().filter(EndConditionResolver::finite)
                .min(Comparator.naturalOrder()).orElse(Double.NaN);
        double max = values.subList(0, end + 1).stream().filter(EndConditionResolver::finite)
                .max(Comparator.naturalOrder()).orElse(Double.NaN);
        if (!finite(min) || !finite(max) || max - min <= EPSILON)
            return Double.NaN;
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
            for (int i = 1; i < crossings.size(); i++)
                halfPeriods.add(crossings.get(i) - crossings.get(i - 1));
            return 2 * median(halfPeriods);
        }
        return Double.NaN;
    }

    private static Double crossing(List<Double> values, List<Double> times, double limit,
            java.util.function.DoublePredicate predicate, double target, ComparisonOperator operator) {
        if (values == null || times == null || values.isEmpty() || values.size() != times.size())
            return null;
        if (times.get(0) <= limit + EPSILON && predicate.test(values.get(0)))
            return times.get(0);
        for (int i = 1; i < values.size() && i < times.size(); i++) {
            if (times.get(i) > limit + EPSILON)
                break;
            if (predicate.test(values.get(i))) {
                double previous = values.get(i - 1);
                double current = values.get(i);
                if (crosses(previous, current, target, operator))
                    return interpolate(previous, current, times.get(i - 1), times.get(i), target);
                return times.get(i);
            }
            if (crosses(values.get(i - 1), values.get(i), target, operator)) {
                return interpolate(values.get(i - 1), values.get(i), times.get(i - 1), times.get(i), target);
            }
        }
        return null;
    }

    private static Double differenceCrossing(List<Double> first, List<Double> second, List<Double> times,
            double limit) {
        if (first == null || second == null || first.size() != second.size())
            return null;
        List<Double> difference = new ArrayList<>();
        for (int i = 0; i < first.size(); i++)
            difference.add(first.get(i) - second.get(i));
        return crossing(difference, times, limit, value -> Math.abs(value) <= EPSILON, 0,
                ComparisonOperator.EQUAL);
    }

    private static Series findSeries(SolverOutput output, OutputSourceBinding source) {
        if (output == null || source == null)
            return null;
        String key = source.key().toLowerCase(Locale.ROOT);
        return switch (source.group()) {
            case VALUES -> named(output.values(), key);
            case POSITIONS -> named(output.positions(), key);
            case VELOCITIES -> named(output.velocities(), key);
            case ACCELERATIONS -> named(output.accelerations(), key);
            case LEGACY_AUTO -> firstNamed(output, key);
            case LEGACY_ENTITY_POSITION -> findNamedSeries(output.positions(), source.key());
        };
    }

    private static Series firstNamed(SolverOutput output, String key) {
        Series value = named(output.values(), key);
        if (value != null) return value;
        value = named(output.positions(), key);
        if (value != null) return value;
        value = named(output.velocities(), key);
        return value != null ? value : named(output.accelerations(), key);
    }

    private static Series named(Map<String, List<Double>> values, String requested) {
        if (values == null)
            return null;
        return values.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(requested))
                .map(entry -> new Series(entry.getKey(), entry.getValue())).findFirst().orElse(null);
    }

    private static Series findNamedSeries(Map<String, List<Double>> values, String requested) {
        if (values == null)
            return null;
        return values.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(requested)
                || compact(entry.getKey()).equals(compact(requested)))
                .map(entry -> new Series(entry.getKey(), entry.getValue())).findFirst().orElse(null);
    }

    private static boolean matches(double value, ComparisonOperator operator, double target) {
        return switch (operator) {
            case GREATER_OR_EQUAL -> value >= target;
            case LESS_OR_EQUAL -> value <= target;
            case GREATER -> value > target;
            case LESS -> value < target;
            case EQUAL -> Math.abs(value - target) <= EPSILON;
            default -> false;
        };
    }

    private static boolean crosses(double before, double current, double target, ComparisonOperator operator) {
        if (!finite(before) || !finite(current))
            return false;
        return switch (operator) {
            case GREATER_OR_EQUAL, GREATER -> before < target && current >= target;
            case LESS_OR_EQUAL, LESS -> before > target && current <= target;
            case EQUAL -> (before - target) * (current - target) <= 0 && Math.abs(before - current) > EPSILON;
            default -> false;
        };
    }

    private static double interpolate(double before, double current, double beforeTime, double currentTime,
            double target) {
        if (Math.abs(current - before) <= EPSILON)
            return currentTime;
        double ratio = (target - before) / (current - before);
        return beforeTime + Math.max(0, Math.min(1, ratio)) * (currentTime - beforeTime);
    }

    private static double interpolate(List<Double> values, List<Double> times, double target) {
        int last = times.size() - 1;
        for (int i = 1; i <= last; i++) {
            if (target <= times.get(i))
                return interpolateAtTime(values.get(i - 1), values.get(i),
                        times.get(i - 1), times.get(i), target);
        }
        return values.get(last);
    }

    private static double interpolateAtTime(double before, double current, double beforeTime,
            double currentTime, double targetTime) {
        if (Math.abs(currentTime - beforeTime) <= EPSILON)
            return current;
        double ratio = (targetTime - beforeTime) / (currentTime - beforeTime);
        return before + Math.max(0, Math.min(1, ratio)) * (current - before);
    }

    private static void validateMaxTime(JsonNode condition, List<String> errors) {
        if (condition.has("maxTime") && !condition.get("maxTime").isNull()
                && (!finitePositive(condition.get("maxTime"))
                        || condition.get("maxTime").asDouble() > MAX_DYNAMIC_SECONDS)) {
            errors.add("endCondition.maxTime must be finite, positive and <= " + MAX_DYNAMIC_SECONDS);
        }
    }

    private static boolean allText(JsonNode values) {
        for (JsonNode value : values)
            if (!value.isTextual() || value.asText().isBlank())
                return false;
        return true;
    }

    private static boolean finite(JsonNode node) {
        return node != null && node.isNumber() && finite(node.asDouble());
    }

    private static boolean finitePositive(JsonNode node) {
        return finite(node) && node.asDouble() > 0;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static boolean finitePositive(double value) {
        return finite(value) && value > 0;
    }

    private static double number(JsonNode node, double fallback) {
        return finite(node) ? node.asDouble() : Math.max(0.01, fallback);
    }

    private static String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static double median(List<Double> values) {
        if (values.isEmpty())
            return Double.NaN;
        List<Double> sorted = values.stream().filter(EndConditionResolver::finite).sorted().toList();
        if (sorted.isEmpty())
            return Double.NaN;
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }

    private record Series(String name, List<Double> values) {
    }
}
