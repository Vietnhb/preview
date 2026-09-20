package com.example.backend.physics.compatibility;

/**
 * Versioned bridge for historical dimension-suffixed schema/model identifiers.
 * Current routing and catalog lookup must use exact identifiers.
 */
public final class LegacySchemaIdentityAdapter {
    public static final String VERSION = "legacy-dimensional-suffix-v1";

    private LegacySchemaIdentityAdapter() { }

    public static String adaptForReplay(String identifier, String adapterVersion) {
        if (identifier == null || identifier.isBlank()) return identifier;
        if (!VERSION.equals(adapterVersion)) {
            throw new IllegalArgumentException("Unsupported legacy schema identity adapter version: " + adapterVersion);
        }
        return identifier.replaceFirst("(?i)[_-](?:1d|2d)$", "");
    }
}
