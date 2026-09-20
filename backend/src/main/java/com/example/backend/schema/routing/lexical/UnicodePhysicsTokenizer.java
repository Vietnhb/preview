package com.example.backend.schema.routing.lexical;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A schema-agnostic Unicode tokenizer for physics search text.
 *
 * <p>Compatibility normalization folds presentation variants such as superscript digits into
 * searchable text. Letters and numbers remain in tokens, while mathematical and unit symbols
 * are emitted as individual tokens. Identifier runs also emit their underscore-separated and
 * letter/number components, so canonical keys remain searchable without hiding their parts.</p>
 */
public final class UnicodePhysicsTokenizer {

    public List<String> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);

        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int[] codePoints = normalized.codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            int codePoint = codePoints[index];
            if (isWordCodePoint(codePoint)
                    || isNumericSeparator(codePoints, index)
                    || isIdentifierConnector(codePoints, index)) {
                word.appendCodePoint(codePoint);
                continue;
            }

            flushWord(word, tokens);
            if (isPhysicsSymbol(codePoint)) {
                tokens.add(new String(Character.toChars(codePoint)));
            }
        }
        flushWord(word, tokens);
        return List.copyOf(tokens);
    }

    private static boolean isWordCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean isIdentifierConnector(int[] codePoints, int index) {
        return codePoints[index] == '_'
                && index > 0
                && index + 1 < codePoints.length
                && isWordCodePoint(codePoints[index - 1])
                && isWordCodePoint(codePoints[index + 1]);
    }

    private static boolean isNumericSeparator(int[] codePoints, int index) {
        int codePoint = codePoints[index];
        return (codePoint == '.' || codePoint == ',')
                && index > 0
                && index + 1 < codePoints.length
                && Character.isDigit(codePoints[index - 1])
                && Character.isDigit(codePoints[index + 1]);
    }

    private static boolean isPhysicsSymbol(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.MATH_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL
                || switch (codePoint) {
                    case '/', '^', '*', '.', '-', '+', ':', '%', '\u00b7', '\u00b0', '\u2032', '\u2033' -> true;
                    default -> false;
                };
    }

    private static void flushWord(StringBuilder word, List<String> tokens) {
        if (word.isEmpty()) {
            return;
        }

        String complete = word.toString();
        List<String> wordTokens = new ArrayList<>();
        addCaseAware(complete, wordTokens);
        for (String component : complete.split("_+")) {
            if (!component.isEmpty()) {
                addCaseAware(component, wordTokens);
                addScriptAndDigitComponents(component, wordTokens);
            }
        }
        tokens.addAll(wordTokens.stream().distinct().toList());
        word.setLength(0);
    }

    private static void addScriptAndDigitComponents(String component, List<String> tokens) {
        int[] points = component.codePoints().toArray();
        if (points.length < 2) {
            return;
        }

        StringBuilder part = new StringBuilder();
        int previousGroup = group(points, 0);
        part.appendCodePoint(points[0]);
        for (int index = 1; index < points.length; index++) {
            int currentGroup = group(points, index);
            if (currentGroup != previousGroup) {
                addCaseAware(part.toString(), tokens);
                part.setLength(0);
            }
            part.appendCodePoint(points[index]);
            previousGroup = currentGroup;
        }
        if (!part.isEmpty()) {
            addCaseAware(part.toString(), tokens);
        }
    }

    /** Fold prose but preserve case for Greek symbols and indexed one-letter Latin identifiers. */
    private static void addCaseAware(String token, List<String> tokens) {
        int[] points = token.codePoints().toArray();
        boolean greekSymbol = false;
        for (int codePoint : points) {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.GREEK) {
                greekSymbol = true;
                break;
            }
        }
        boolean indexedLatinSymbol = points.length > 0
                && Character.UnicodeScript.of(points[0]) == Character.UnicodeScript.LATIN
                && Character.isUpperCase(points[0]);
        if (indexedLatinSymbol) {
            for (int index = 1; index < points.length; index++) {
                int codePoint = points[index];
                if (!Character.isDigit(codePoint) && codePoint != '_') {
                    indexedLatinSymbol = false;
                    break;
                }
            }
        }
        tokens.add(greekSymbol || indexedLatinSymbol ? token : token.toLowerCase(Locale.ROOT));
    }

    private static int group(int[] codePoints, int index) {
        int codePoint = codePoints[index];
        if (Character.isDigit(codePoint)) {
            return 1;
        }
        if ((codePoint == '.' || codePoint == ',')
                && index > 0
                && index + 1 < codePoints.length
                && Character.isDigit(codePoints[index - 1])
                && Character.isDigit(codePoints[index + 1])) {
            return 1;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return switch (script) {
            case GREEK -> 2;
            case CYRILLIC -> 3;
            case LATIN -> 4;
            default -> 5 + script.ordinal();
        };
    }
}
