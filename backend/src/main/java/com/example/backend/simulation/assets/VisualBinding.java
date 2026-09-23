package com.example.backend.simulation.assets;

/** Associates a render slot with an extracted entity and a JEV-approved catalog choice. */
public record VisualBinding(String targetId, String entityId, String assetId, String match, String visualDifference) {
    public VisualBinding(String targetId, String entityId, String assetId, String match) {
        this(targetId, entityId, assetId, match, null);
    }
}
