package com.example.backend.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Deterministic fallback for the mandatory MVP archetypes. It extracts only
 * explicit number/unit pairs; unresolved required fields remain ambiguities and
 * are never filled with a guessed value.
 */
@Component
public class RuleBasedExtractionProvider implements ExtractionProvider {
    private static final String INITIAL_POSITION = "initial_position";
    private static final String INITIAL_HEIGHT = "initial_height";
    private static final String INITIAL_VELOCITY = "initial_velocity";
    private static final String ACCELERATION = "acceleration";
    private static final String LAUNCH_ANGLE = "launch_angle";
    private static final String FRICTION_COEFFICIENT = "friction_coefficient";
    private static final String VELOCITY_2 = "velocity_2";
    private static final String VOLTAGE = "voltage";
    private static final String RESISTANCE = "resistance";
    private static final String CAPACITANCE = "capacitance";
    private static final String DYNAMICS_COLLISION = "dynamics_collision";
    private static final String OSCILLATIONS_SPRING = "oscillations_spring";
    private static final String DYNAMICS_FORCES = "dynamics_forces";
    private static final String KINEMATICS_PROJECTILE = "kinematics_projectile";
    private static final String KINEMATICS_1D = "kinematics_1d";
    private static final String NET_FORCE = "net_force";
    private static final String AMPLITUDE = "amplitude";
    private static final String SPRING_CONSTANT = "spring_constant";
    private static final String PHASE = "phase";

    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "(?<!\\w)([-+]?\\d+(?:[.,]\\d+)?)\\s*([\\p{L}Ω°]+(?:/[\\p{L}0-9²^]+)?)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NUMBER_ONLY = Pattern.compile(
            "(?<![\\p{L}\\w.])([-+]?\\d+(?:[.,]\\d+)?)(?!\\s*[\\p{L}\\w])");
    private final UnitNormalizer unitNormalizer;

    public RuleBasedExtractionProvider(UnitNormalizer unitNormalizer) {
        this.unitNormalizer = unitNormalizer;
    }

    @Override
    public String providerName() { return "rule-based"; }

    @Override
    public String modelVersion() { return "catalog-v1"; }

    @Override
    public boolean isAvailable() { return true; }

    /** Parses an explicit teacher answer for a missing quantity; never guesses a value. */
    public PhysicalQuantity explicitQuantity(String key, String answer) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("A quantity key and an explicit value are required.");
        }
        String trimmed = answer.trim();
        Matcher matcher = NUMBER_WITH_UNIT.matcher(trimmed);
        String originalUnit;
        String rawValue;
        if (matcher.find()) {
            rawValue = matcher.group(1);
            originalUnit = matcher.group(2);
        } else if (FRICTION_COEFFICIENT.equalsIgnoreCase(key)) {
            Matcher dimensionless = NUMBER_ONLY.matcher(trimmed);
            if (!dimensionless.find()) throw new IllegalArgumentException("Include a numeric value, for example 0.2.");
            rawValue = dimensionless.group(1);
            originalUnit = "1";
        } else {
            throw new IllegalArgumentException("Include a numeric value and unit, for example 2 kg.");
        }
        BigDecimal value = new BigDecimal(rawValue.replace(',', '.'));
        UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(value, originalUnit);
        if (!normalized.knownUnit()) throw new IllegalArgumentException("The supplied unit is not supported.");
        if (VELOCITY_2.equalsIgnoreCase(key)
                && value.signum() >= 0
                && containsAny(trimmed.toLowerCase(Locale.ROOT), "ngược", "opposite", "reverse")) {
            normalized = new UnitNormalizer.NormalizedQuantity(normalized.value(), normalized.originalUnit(),
                    normalized.normalizedValue().negate(), normalized.normalizedUnit(), true);
        }
        return new PhysicalQuantity(key, symbol(key), normalized.value(), normalized.originalUnit(),
                normalized.normalizedValue(), normalized.normalizedUnit(), BigDecimal.ONE, answer.trim());
    }

    @Override
    public ProviderExtractionResult extract(String text) {
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException("Problem text must not be blank.");
        String source = text.trim();
        String lower = source.toLowerCase(Locale.ROOT);
        String schemaId = detectSchema(lower);
        List<PhysicalQuantity> quantities = quantities(source, schemaId, lower);
        List<AmbiguityItem> ambiguities = missing(schemaId, quantities);
        String topic = topicFor(schemaId);
        SpecificationDocument document = new SpecificationDocument(
                SpecificationDocument.CURRENT_SCHEMA_VERSION,
                topic,
                schemaId,
                List.of(new PhysicalObject("object_1", "Vật thể", "physical_object")),
                quantities,
                List.of(),
                BigDecimal.valueOf(0.72),
                ambiguities);
        return new ProviderExtractionResult(document, null);
    }

    private String detectSchema(String text) {
        if (containsAny(text, "mạch", "điện", "tụ điện", "điện trở", "circuit", "resistor", "capacitor", VOLTAGE)) {
            return containsAny(text, "xả", "phóng điện", "discharg") ? "circuits_rc_discharging" : "circuits_rc_charging";
        }
        if (containsAny(text, "va chạm", "đàn hồi", "collision")) return DYNAMICS_COLLISION;
        if (containsAny(text, "lò xo", "độ cứng", "spring")) return OSCILLATIONS_SPRING;
        if (containsAny(text, "lực", "ma sát", "force", "friction")) return DYNAMICS_FORCES;
        if (containsAny(text, "ném", "góc", "parabol", "projectile", "thrown", "launch", "đạn")) return KINEMATICS_PROJECTILE;
        if (containsAny(text, "chuyển động", "vận tốc", "gia tốc", "vật chuyển động", "kinematics", "motion", "moving", "velocity", ACCELERATION, "position")) return KINEMATICS_1D;
        throw new IllegalArgumentException("Unsupported physics topic. Choose Kinematics, Dynamics, or Circuits.");
    }

    private List<PhysicalQuantity> quantities(String source, String schemaId, String lower) {
        List<Matched> matches = new ArrayList<>();
        Matcher matcher = NUMBER_WITH_UNIT.matcher(source);
        while (matcher.find()) {
            String rawValue = matcher.group(1).replace(',', '.');
            String rawUnit = matcher.group(2);
            UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(new BigDecimal(rawValue), rawUnit);
            if (normalized.knownUnit()) matches.add(new Matched(matcher.start(), matcher.group(), normalized));
        }
        return assign(matches, schemaId, lower);
    }

    private List<PhysicalQuantity> assign(List<Matched> matches, String schemaId, String text) {
        List<PhysicalQuantity> result = new ArrayList<>();
        AssignmentCounters counters = new AssignmentCounters();
        for (Matched match : matches) {
            String key = keyFor(match, schemaId, counters);
            if (DYNAMICS_COLLISION.equals(schemaId) && VELOCITY_2.equals(key)
                    && containsAny(text, "ngược chiều", "ngược hướng")) {
                match = match.withValue(match.normalized.normalizedValue().negate());
            }
            String assignedKey = key;
            if (assignedKey != null && result.stream().noneMatch(item -> item.name().equals(assignedKey))) {
                result.add(new PhysicalQuantity(assignedKey, symbol(assignedKey), match.normalized.value(), match.normalized.originalUnit(),
                        match.normalized.normalizedValue(), match.normalized.normalizedUnit(), BigDecimal.valueOf(0.75), match.source));
            }
        }
        return List.copyOf(result);
    }

    private String topicFor(String schemaId) {
        if (schemaId.startsWith("kinematics")) return "KINEMATICS";
        if (schemaId.startsWith("dynamics")) return "DYNAMICS";
        return "CIRCUITS";
    }

    private String keyFor(Matched match, String schemaId, AssignmentCounters counters) {
        if (DYNAMICS_COLLISION.equals(schemaId)) return collisionKey(match, counters);
        if (KINEMATICS_PROJECTILE.equals(schemaId)) return projectileKey(match, counters);
        if (KINEMATICS_1D.equals(schemaId)) return oneDimensionalKey(match, counters);
        if (DYNAMICS_FORCES.equals(schemaId)) return forcesKey(match, counters);
        if (OSCILLATIONS_SPRING.equals(schemaId)) return springKey(match, counters);
        return circuitKey(match, schemaId);
    }

    private String collisionKey(Matched match, AssignmentCounters counters) {
        String unit = match.normalized.normalizedUnit();
        if (unit.equals("kg") && counters.mass < 2) return "mass_" + (++counters.mass);
        if (unit.equals("m/s") && counters.velocity < 2) {
            counters.velocity++;
            return "velocity_" + counters.velocity;
        }
        if (unit.equals("m") && counters.position < 2) return "initial_position_" + (++counters.position);
        return null;
    }

    private String projectileKey(Matched match, AssignmentCounters counters) {
        String unit = match.normalized.normalizedUnit();
        if (unit.equals("m/s")) return INITIAL_VELOCITY;
        if (unit.equals("rad")) return LAUNCH_ANGLE;
        if (unit.equals("m") && counters.position < 2) {
            return counters.position++ == 0 ? INITIAL_POSITION : INITIAL_HEIGHT;
        }
        return null;
    }

    private String oneDimensionalKey(Matched match, AssignmentCounters counters) {
        String unit = match.normalized.normalizedUnit();
        if (unit.equals("m/s")) return INITIAL_VELOCITY;
        if (unit.equals("m/s2")) return ACCELERATION;
        if (unit.equals("m") && counters.position++ == 0) return INITIAL_POSITION;
        return null;
    }

    private String forcesKey(Matched match, AssignmentCounters counters) {
        String unit = match.normalized.normalizedUnit();
        if (unit.equals("kg")) return "mass";
        if (unit.equals("N")) return NET_FORCE;
        if (unit.equals("m/s")) return INITIAL_VELOCITY;
        if (unit.equals("m") && counters.position++ == 0) return INITIAL_POSITION;
        return null;
    }

    private String springKey(Matched match, AssignmentCounters counters) {
        String unit = match.normalized.normalizedUnit();
        if (unit.equals("m") && counters.position++ == 0) return AMPLITUDE;
        if (unit.equals("kg")) return "mass";
        if (unit.equals("N/m")) return SPRING_CONSTANT;
        if (unit.equals("rad")) return PHASE;
        return null;
    }

    private String circuitKey(Matched match, String schemaId) {
        if (!schemaId.startsWith("circuits_rc_")) return null;
        return switch (match.normalized.normalizedUnit()) {
            case "V" -> VOLTAGE;
            case "ohm" -> RESISTANCE;
            case "F" -> CAPACITANCE;
            default -> null;
        };
    }

    private static final class AssignmentCounters {
        private int mass;
        private int position;
        private int velocity;
    }

    private List<AmbiguityItem> missing(String schemaId, List<PhysicalQuantity> quantities) {
        List<String> required = switch (schemaId) {
            case KINEMATICS_1D -> List.of(INITIAL_POSITION, INITIAL_VELOCITY, ACCELERATION);
            case KINEMATICS_PROJECTILE -> List.of(INITIAL_POSITION, INITIAL_HEIGHT, INITIAL_VELOCITY, LAUNCH_ANGLE);
            case DYNAMICS_FORCES -> List.of("mass", NET_FORCE, FRICTION_COEFFICIENT, INITIAL_POSITION, INITIAL_VELOCITY);
            case DYNAMICS_COLLISION -> List.of("mass_1", "mass_2", "initial_position_1", "initial_position_2", "velocity_1", VELOCITY_2);
            case OSCILLATIONS_SPRING -> List.of(AMPLITUDE, "mass", SPRING_CONSTANT, PHASE);
            default -> List.of(VOLTAGE, RESISTANCE, CAPACITANCE);
        };
        List<AmbiguityItem> result = new ArrayList<>();
        for (String key : required) if (quantities.stream().noneMatch(item -> item.name().equals(key))) {
            result.add(new AmbiguityItem("schema.required." + key, "quantities." + key,
                    "Vui lòng cung cấp giá trị và đơn vị cho " + key + ".", List.of()));
        }
        return List.copyOf(result);
    }

    private String symbol(String key) {
        return Map.ofEntries(
                Map.entry(INITIAL_POSITION, "x0"), Map.entry(INITIAL_HEIGHT, "y0"),
                Map.entry(INITIAL_VELOCITY, "v0"), Map.entry(ACCELERATION, "a"),
                Map.entry("mass", "m"), Map.entry("mass_1", "m1"), Map.entry("mass_2", "m2"),
                Map.entry("initial_position_1", "x1_0"), Map.entry("initial_position_2", "x2_0"),
                Map.entry("velocity_1", "v1"), Map.entry(VELOCITY_2, "v2"),
                Map.entry(LAUNCH_ANGLE, "theta"), Map.entry(NET_FORCE, "F"),
                Map.entry(FRICTION_COEFFICIENT, "mu"), Map.entry(AMPLITUDE, "A"),
                Map.entry(SPRING_CONSTANT, "k"), Map.entry(PHASE, "phi"),
                Map.entry(VOLTAGE, "V"), Map.entry(RESISTANCE, "R"), Map.entry(CAPACITANCE, "C")
        ).getOrDefault(key, key);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private record Matched(int start, String source, UnitNormalizer.NormalizedQuantity normalized) {
        private Matched withValue(BigDecimal value) {
            return new Matched(start, source, new UnitNormalizer.NormalizedQuantity(value,
                    normalized.originalUnit(), value, normalized.normalizedUnit(), normalized.knownUnit()));
        }
    }
}
