package com.example.backend.schema.routing.model;

import java.util.Objects;

/** One bounded, reviewed natural-language view used for semantic retrieval. */
public record SemanticSearchView(
        String locale,
        String viewType,
        String viewVersion,
        String text) {

    public SemanticSearchView {
        locale = required(locale, "locale");
        viewType = required(viewType, "viewType");
        viewVersion = required(viewVersion, "viewVersion");
        text = required(text, "text");
        if (!locale.equals("en") && !locale.equals("vi")) {
            throw new IllegalArgumentException("Semantic view locale must be en or vi");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
