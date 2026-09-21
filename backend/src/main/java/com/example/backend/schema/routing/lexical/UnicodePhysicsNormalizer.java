package com.example.backend.schema.routing.lexical;

import java.text.Normalizer;

/** Schema-agnostic normalization shared by lexical retrieval and contract evidence. */
public final class UnicodePhysicsNormalizer {
    private UnicodePhysicsNormalizer() { }

    /** Applies compatibility normalization without changing decimal separators or values. */
    public static String normalize(String text) {
        if (text == null) throw new IllegalArgumentException("Text must not be null");
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-')
                .replace('\u00D7', '*')
                .replace('\u00B7', '*');
        return normalized.replaceAll("\\s+", " ").trim();
    }

    /** Accent-folded shadow text used only for recall; canonical text remains accented. */
    public static String accentFold(String text) {
        String normalized = normalize(text);
        String decomposed = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
        return Normalizer.normalize(decomposed.replaceAll("\\p{M}+", ""), Normalizer.Form.NFC);
    }
}
