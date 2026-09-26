package com.example.backend.ai.extraction.model;

import java.util.List;

public record PhysicalObject(String id, String label, String type, List<PhysicalQuantity> quantities) {
    public PhysicalObject {
        quantities = quantities == null ? List.of() : List.copyOf(quantities);
    }
}
