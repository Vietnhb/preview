package com.example.backend.schema.routing.lexical;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnicodePhysicsTokenizerTest {
    private final UnicodePhysicsTokenizer tokenizer = new UnicodePhysicsTokenizer();

    @Test
    void tokenizesVietnameseGreekSymbolsAndUnits() {
        List<String> tokens = tokenizer.tokenize("T\u00ednh v\u1eadn t\u1ed1c \u03bb = 2 m/s\u00b2; \u0394x");

        List<String> expected = List.of("t\u00ednh", "v\u1eadn", "t\u1ed1c", "\u03bb", "=", "2", "m", "/", "s2", "s", "\u0394x", "\u0394", "x");
        assertTrue(tokens.containsAll(expected), () -> "Missing " + expected.stream()
                .filter(token -> !tokens.contains(token)).map(UnicodePhysicsTokenizerTest::codePoints).toList()
                + " in " + tokens.stream().map(UnicodePhysicsTokenizerTest::codePoints).toList());
        org.junit.jupiter.api.Assertions.assertFalse(tokens.contains("\u03b4"));
        org.junit.jupiter.api.Assertions.assertFalse(tokens.contains("\u03b4x"));
    }

    @Test
    void preservesRepeatedTermsForTermFrequency() {
        List<String> tokens = tokenizer.tokenize("mass mass");

        org.junit.jupiter.api.Assertions.assertEquals(List.of("mass", "mass"), tokens);
    }

    @Test
    void accentFoldedShadowMatchesVietnameseWithoutChangingDecimalValues() {
        List<String> accented = tokenizer.tokenizeWithAccentShadow("T\u1ee5 \u0111i\u1ec7n 1,25 V; \u0394t = 2 s");
        List<String> unaccented = tokenizer.tokenizeWithAccentShadow("Tu dien 1,25 V; \u0394t = 2 s");

        org.junit.jupiter.api.Assertions.assertTrue(accented.contains("t\u1ee5"));
        org.junit.jupiter.api.Assertions.assertTrue(accented.contains("tu"));
        org.junit.jupiter.api.Assertions.assertTrue(accented.contains("1,25"));
        org.junit.jupiter.api.Assertions.assertEquals(unaccented, tokenizer.tokenizeWithAccentShadow(
                java.text.Normalizer.normalize("Tu dien 1,25 V; Δt = 2 s", java.text.Normalizer.Form.NFC)));
    }

    @Test
    void normalizesDecomposedVietnameseAndCommonOcrSpacing() {
        String decomposed = "\u0110ie\u0302\u0323n   tr\u01a1\u0309  qua\u00a0R";
        List<String> tokens = tokenizer.tokenizeWithAccentShadow(decomposed);

        org.junit.jupiter.api.Assertions.assertTrue(tokens.contains("\u0111i\u1ec7n"));
        org.junit.jupiter.api.Assertions.assertTrue(tokens.contains("dien"));
        org.junit.jupiter.api.Assertions.assertTrue(tokens.contains("tr\u1edf"));
        org.junit.jupiter.api.Assertions.assertTrue(tokens.contains("tro"));
        org.junit.jupiter.api.Assertions.assertTrue(tokens.contains("R"));
    }

    @Test
    void keepsPhysicsUnitsAndDecimalSeparatorsAsSearchableTokens() {
        List<String> tokens = tokenizer.tokenizeWithAccentShadow("12,5 N; 3.2 V; 4 A; 5 ohm; 6 Ω; 7 F; 8 Hz; 9 rad");

        org.junit.jupiter.api.Assertions.assertTrue(tokens.containsAll(List.of(
                "12,5", "3.2", "N", "V", "A", "ohm", "Ω", "F", "hz", "rad")));
    }

    @Test
    void foldsOrdinaryProseButPreservesCaseDistinctSymbols() {
        org.junit.jupiter.api.Assertions.assertEquals(tokenizer.tokenize("mass"), tokenizer.tokenize("Mass"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("R"), tokenizer.tokenize("R"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("r"), tokenizer.tokenize("r"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("\u0394"), tokenizer.tokenize("\u0394"));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("\u03b4"), tokenizer.tokenize("\u03b4"));
    }

    private static String codePoints(String text) {
        return text.codePoints().mapToObj(point -> String.format("U+%04X", point))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
