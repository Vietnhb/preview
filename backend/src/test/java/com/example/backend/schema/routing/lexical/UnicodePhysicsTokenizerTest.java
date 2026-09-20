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
