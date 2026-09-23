package com.example.backend.ai.extraction;

/** Reserved field paths for backend-owned compatibility decisions. */
public final class CompatibilityFieldPaths {
    public static final String CAPACITY = "compatibility.capacity";
    public static final String CAPACITY_RETAINED_OBJECT = CAPACITY + ".retainedObject";

    private CompatibilityFieldPaths() { }

    public static boolean isCapacity(String fieldPath) {
        return CAPACITY.equals(fieldPath) || CAPACITY_RETAINED_OBJECT.equals(fieldPath);
    }
}
