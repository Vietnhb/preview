package com.example.backend.schema.routing.lexical;

import java.util.Set;

/** Global, language-level stop words; no schema or lesson is allowed to add rules here. */
public final class UnicodePhysicsStopWords {
    private static final Set<String> DEFAULT = Set.of(
            "a", "an", "and", "the", "is", "are", "of", "to", "in", "for", "with", "from", "on", "at", "by",
            "một", "và", "là", "có", "được", "qua", "theo", "trong", "của", "cho", "với");

    private UnicodePhysicsStopWords() { }

    public static boolean isStopWord(String token) {
        return token != null && DEFAULT.contains(token);
    }
}
