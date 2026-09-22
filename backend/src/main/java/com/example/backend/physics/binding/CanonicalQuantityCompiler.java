package com.example.backend.physics.binding;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.exception.CanonicalContractException;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.service.problem.CompiledSchema;
import com.fasterxml.jackson.databind.JsonNode;

/** Compiles already-canonical specification quantities once at physics ingress. */
@Component
public final class CanonicalQuantityCompiler {
    private static final double NORMALIZED_VALUE_TOLERANCE = 1e-12;
    private final UnitNormalizer units;

    public CanonicalQuantityCompiler(UnitNormalizer units) {
        this.units = units;
    }

    public CanonicalQuantityBag compile(CompiledSchema schema, JsonNode specification,
            Map<String, Double> effectiveAdjustments) {
        if (schema == null || specification == null || !specification.isObject()) {
            throw new CanonicalContractException("Pinned compiled schema and specification are required");
        }
        JsonNode quantityNodes = specification.get("quantities");
        if (quantityNodes == null || !quantityNodes.isArray()) {
            throw new CanonicalContractException("Canonical quantities must be an array");
        }

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        Map<String, String> normalizedUnits = new LinkedHashMap<>();
        for (JsonNode quantity : quantityNodes) {
            if (!quantity.isObject()) throw new CanonicalContractException("Canonical quantity entries must be objects");
            JsonNode name = quantity.get("name");
            if (name == null || !name.isTextual() || name.asText().isBlank()
                    || !name.asText().equals(name.asText().trim())) {
                throw new CanonicalContractException("Canonical quantity name must be an exact schema key");
            }
            String key = name.asText();
            CompiledSchema.QuantityDefinition definition = schema.quantities().get(key);
            if (definition == null) {
                throw new CanonicalContractException("Quantity key is not canonical for "
                        + schema.schemaId() + "@" + schema.version());
            }
            if (values.containsKey(key)) {
                throw new CanonicalContractException("Duplicate canonical quantity " + key + " in "
                        + schema.schemaId() + "@" + schema.version());
            }
            NormalizedValue normalized = normalize(quantity, key, schema);
            requireContract(schema, definition, key, normalized.value(), normalized.unit());
            values.put(key, normalized.value());
            normalizedUnits.put(key, normalized.unit());
        }

        for (CompiledSchema.QuantityDefinition definition : schema.quantities().values()) {
            if (values.containsKey(definition.key()) || definition.defaultValue() == null) continue;
            String unit = definition.defaultUnit();
            UnitNormalizer.NormalizedQuantity normalized = units.normalize(BigDecimal.valueOf(definition.defaultValue()), unit);
            if (!normalized.knownUnit()) {
                throw new CanonicalContractException("Compiled default unit is absent from the unit catalog for "
                        + schema.schemaId() + "." + definition.key());
            }
            requireContract(schema, definition, definition.key(), normalized.normalizedValue(), normalized.normalizedUnit());
            values.put(definition.key(), normalized.normalizedValue());
            normalizedUnits.put(definition.key(), normalized.normalizedUnit());
        }

        if (effectiveAdjustments != null) {
            for (Map.Entry<String, Double> adjustment : effectiveAdjustments.entrySet()) {
                String adjustmentKey = adjustment.getKey();
                CompiledSchema.AdjustmentDefinition contract = adjustmentKey == null
                        ? null : schema.adjustments().get(adjustmentKey);
                if (contract == null || adjustment.getValue() == null || !Double.isFinite(adjustment.getValue())
                        || adjustment.getValue() < contract.min() || adjustment.getValue() > contract.max()) {
                    throw new CanonicalContractException("Adjustment is outside the compiled schema contract for "
                            + schema.schemaId() + "." + adjustmentKey);
                }
                CompiledSchema.QuantityDefinition quantity = schema.quantities().get(adjustmentKey);
                if (quantity == null) {
                    throw new CanonicalContractException("Adjustment has no compiled quantity contract for "
                            + schema.schemaId() + "." + adjustmentKey);
                }
                String unit = normalizedUnits.get(quantity.key());
                if (unit == null && quantity.defaultValue() != null) {
                    UnitNormalizer.NormalizedQuantity normalizedDefault = units.normalize(
                            BigDecimal.valueOf(quantity.defaultValue()), quantity.defaultUnit());
                    if (normalizedDefault.knownUnit()) unit = normalizedDefault.normalizedUnit();
                }
                if (unit == null) unit = canonicalAllowedUnit(quantity);
                BigDecimal value = BigDecimal.valueOf(adjustment.getValue());
                requireContract(schema, quantity, quantity.key(), value, unit);
                values.put(quantity.key(), value);
                normalizedUnits.put(quantity.key(), unit);
            }
        }

        try {
            return new CanonicalQuantityBag(values, normalizedUnits);
        } catch (IllegalArgumentException failure) {
            throw new CanonicalContractException("Compiled quantity set is invalid for "
                    + schema.schemaId() + "@" + schema.version(), failure);
        }
    }

    private NormalizedValue normalize(JsonNode quantity, String key, CompiledSchema schema) {
        JsonNode rawValue = quantity.get("value");
        JsonNode originalUnitNode = quantity.get("originalUnit");
        if (rawValue == null || !rawValue.isNumber() || originalUnitNode == null
                || !originalUnitNode.isTextual() || originalUnitNode.asText().isBlank()) {
            throw new CanonicalContractException("Quantity requires a numeric raw value and original unit for "
                    + schema.schemaId() + "." + key);
        }
        String originalUnit = originalUnitNode.asText().trim();
        JsonNode normalizedValue = quantity.get("normalizedValue");
        JsonNode normalizedUnitNode = quantity.get("normalizedUnit");
        String normalizedUnit = normalizedUnitNode != null && normalizedUnitNode.isTextual()
                ? normalizedUnitNode.asText().trim() : "";

        UnitNormalizer.NormalizedQuantity normalized = units.normalize(rawValue.decimalValue(), originalUnit);
        if (!normalized.knownUnit()) {
            throw new CanonicalContractException("Input unit is not present in the approved unit catalog for "
                    + schema.schemaId() + "." + key);
        }
        if (normalizedValue != null && (!normalizedValue.isNumber()
                || !approximatelyEqual(normalized.normalizedValue(), normalizedValue.decimalValue()))) {
            throw new CanonicalContractException("Stored normalized quantity disagrees with raw value for "
                    + schema.schemaId() + "." + key);
        }
        if (normalizedUnitNode != null && (!normalizedUnitNode.isTextual()
                || !normalized.normalizedUnit().equals(normalizedUnit))) {
            throw new CanonicalContractException("Stored normalized unit disagrees with raw unit for "
                    + schema.schemaId() + "." + key);
        }
        return new NormalizedValue(normalized.normalizedValue(), normalized.normalizedUnit());
    }

    /**
     * JSON persistence may round a high-precision BigDecimal to the numeric
     * representation used by the API (for example 45 degrees to radians).
     * Accept that serialization round-off while still rejecting a materially
     * different stored normalized value.
     */
    private boolean approximatelyEqual(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) return false;
        double left = expected.doubleValue();
        double right = actual.doubleValue();
        if (!Double.isFinite(left) || !Double.isFinite(right)) return false;
        double tolerance = NORMALIZED_VALUE_TOLERANCE * Math.max(1.0, Math.abs(left));
        return Math.abs(left - right) <= tolerance;
    }

    private void requireContract(CompiledSchema schema, CompiledSchema.QuantityDefinition definition,
            String key, BigDecimal value, String unit) {
        if (value == null || !Double.isFinite(value.doubleValue()) || unit == null
                || !allowsCanonicalUnit(definition, unit)) {
            throw new CanonicalContractException("Quantity value or unit violates the compiled schema for "
                    + schema.schemaId() + "." + key);
        }
        if ((definition.positive() && value.signum() <= 0)
                || (definition.nonNegative() && value.signum() < 0)) {
            throw new CanonicalContractException("Quantity domain constraint failed for "
                    + schema.schemaId() + "." + key);
        }
        if ((definition.minimum() != null && value.compareTo(BigDecimal.valueOf(definition.minimum())) < 0)
                || (definition.maximum() != null && value.compareTo(BigDecimal.valueOf(definition.maximum())) > 0)) {
            throw new CanonicalContractException("Quantity is outside the compiled numeric range for "
                    + schema.schemaId() + "." + key);
        }
        if (definition.integer() && value.stripTrailingZeros().scale() > 0) {
            throw new CanonicalContractException("Quantity must be an integer for "
                    + schema.schemaId() + "." + key);
        }
    }

    private boolean allowsCanonicalUnit(CompiledSchema.QuantityDefinition definition, String canonicalUnit) {
        for (String acceptedUnit : definition.allowedUnits()) {
            UnitNormalizer.NormalizedQuantity normalized = units.normalize(BigDecimal.ONE, acceptedUnit);
            String candidate = normalized.knownUnit() ? normalized.normalizedUnit() : acceptedUnit;
            if (candidate.equals(canonicalUnit)) return true;
        }
        return false;
    }

    private String canonicalAllowedUnit(CompiledSchema.QuantityDefinition definition) {
        return definition.allowedUnits().stream().sorted()
                .map(accepted -> units.normalize(BigDecimal.ONE, accepted))
                .filter(UnitNormalizer.NormalizedQuantity::knownUnit)
                .map(UnitNormalizer.NormalizedQuantity::normalizedUnit)
                .findFirst().orElseGet(() -> definition.allowedUnits().stream().sorted().findFirst().orElse(""));
    }

    private record NormalizedValue(BigDecimal value, String unit) { }
}
