package com.example.backend.ai.normalization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class UnitNormalizer {
    private record UnitDefinition(String canonical, BigDecimal factor) { }
    private final Map<String, UnitDefinition> units;

    public UnitNormalizer(ObjectMapper objectMapper) {
        Map<String, UnitDefinition> loaded = new HashMap<>();
        try (var input = new ClassPathResource("units/catalog.json").getInputStream()) {
            for (JsonNode item : objectMapper.readTree(input)) {
                UnitDefinition definition = new UnitDefinition(item.path("canonical").asText(), item.path("factor").decimalValue());
                for (JsonNode alias : item.path("aliases")) loaded.put(normalizeKey(alias.asText()), definition);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the production unit catalog", exception);
        }
        units = Map.copyOf(loaded);
    }

    public NormalizedQuantity normalize(BigDecimal value, String originalUnit) {
        UnitDefinition definition = units.get(normalizeKey(originalUnit));
        if (definition == null) return new NormalizedQuantity(value, originalUnit, value, originalUnit, false);
        BigDecimal normalizedValue = value.multiply(definition.factor()).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        return new NormalizedQuantity(value, originalUnit, normalizedValue, definition.canonical(), true);
    }

    public String normalizeKey(String unit) {
        if (unit == null) return "";
        return Normalizer.normalize(unit.trim().toLowerCase(), Normalizer.Form.NFKC).replace(" ", "").replace("^2", "2");
    }

    public record NormalizedQuantity(BigDecimal value, String originalUnit, BigDecimal normalizedValue,
            String normalizedUnit, boolean knownUnit) { }
}
