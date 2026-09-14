package com.example.backend.extraction;

import java.math.BigDecimal;

public record PhysicalQuantity(
        String name,
        String symbol,
        BigDecimal value,
        String originalUnit,
        BigDecimal normalizedValue,
        String normalizedUnit,
        BigDecimal confidence,
        String sourceText) {
}
