package com.example.backend.ai.extraction.model;

import java.util.List;

public record PhysicalObject(String id, String label, String type, List<PhysicalQuantity> quantities) {
    public PhysicalObject {
        quantities = quantities == null ? List.of() : List.copyOf(quantities);
    }

    /** Source-compatible constructor for objects without entity-local quantities. */
    public PhysicalObject(String id, String label, String type) {
        this(id, label, type, List.of());
    }
}
