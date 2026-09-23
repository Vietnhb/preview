package com.example.backend.ai.extraction.model;

import java.math.BigDecimal;
import java.util.List;

public final class QuantityContractViolation extends IllegalArgumentException {
    private final String fieldPath;
    private final BigDecimal value;
    private final String suppliedUnit;
    private final List<String> expectedUnits;

    public QuantityContractViolation(String fieldPath, BigDecimal value, String suppliedUnit,
            List<String> expectedUnits, String reason) {
        super(reason);
        this.fieldPath = fieldPath;
        this.value = value;
        this.suppliedUnit = suppliedUnit;
        this.expectedUnits = expectedUnits == null ? List.of() : List.copyOf(expectedUnits);
    }

    public String fieldPath() { return fieldPath; }
    public BigDecimal value() { return value; }
    public String suppliedUnit() { return suppliedUnit; }
    public List<String> expectedUnits() { return expectedUnits; }
}
