package com.example.backend.schema.routing.verification;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection.QuantityProjection;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.schema.routing.index.IndexedSchemaCandidate;
import com.example.backend.schema.routing.lexical.UnicodePhysicsTokenizer;
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;

/** Uses candidate metadata and the shared unit catalog; it contains no schema-specific rules. */
@Component
public final class SchemaContractReranker {
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])([+\\-]?(?:\\d+(?:[.,]\\d+)?|[.,]\\d+)(?:[eE][+\\-]?\\d+)?)\\s*"
                    + "([\\p{L}\\p{M}\\p{N}µμΩΩ°%]+(?:[/·⋅*^][\\p{L}\\p{M}\\p{N}µμΩΩ°%]+)*)");
    private static final Pattern EXPLICIT_VALUE_LINK = Pattern.compile(
            "(?iu)\\s*(?:(?:is\\s+)?equal(?:s)?(?:\\s+to)?|is|:|=|→)\\s*");
    private final UnicodePhysicsTokenizer tokenizer = new UnicodePhysicsTokenizer();
    private final UnitNormalizer units;
    private final SchemaRoutingProperties properties;

    public SchemaContractReranker(UnitNormalizer units, SchemaRoutingProperties properties) {
        this.units = units;
        this.properties = properties;
    }

    public Result verify(String query, IndexedSchemaCandidate candidate) {
        Set<String> queryTerms = new HashSet<>(tokenizer.tokenize(query));
        CandidateContractProjection contract = candidate.contract();
        double quantityCoverage = quantityCoverage(queryTerms, contract.requiredQuantities());
        double unitCompatibility = unitCompatibility(query, contract);
        double metadataOverlap = metadataOverlap(queryTerms, candidate.document().searchText());
        double contradiction = contractContradiction(query, contract);

        double totalWeight = properties.requiredQuantityWeight() + properties.unitCompatibilityWeight()
                + properties.metadataWeight();
        double positiveEvidence = (quantityCoverage * properties.requiredQuantityWeight()
                + unitCompatibility * properties.unitCompatibilityWeight()
                + metadataOverlap * properties.metadataWeight()) / totalWeight;
        double confidence = positiveEvidence * (1.0 - properties.contradictionPenaltyWeight() * contradiction);
        List<String> evidence = new ArrayList<>();
        if (quantityCoverage > 0) evidence.add("REQUIRED_QUANTITY_COVERAGE");
        if (unitCompatibility > 0) evidence.add("UNIT_CONTRACT_MATCH");
        if (metadataOverlap > 0) evidence.add("SCHEMA_METADATA_MATCH");
        if (contradiction > 0) evidence.add("CONTRACT_CONTRADICTION");
        return new Result(confidence, new VerificationEvidence(quantityCoverage, unitCompatibility,
                metadataOverlap, contradiction, evidence));
    }

    private double quantityCoverage(Set<String> queryTerms, List<QuantityProjection> required) {
        if (required.isEmpty()) return 0;
        long matched = required.stream()
                .filter(quantity -> matches(queryTerms, quantity.key(), quantity.aliases(), quantity.symbols())).count();
        return (double) matched / required.size();
    }

    private double unitCompatibility(String query, CandidateContractProjection contract) {
        Set<String> queryUnits = new LinkedHashSet<>();
        String normalizedQuery = Normalizer.normalize(query, Normalizer.Form.NFKC).replace('\u2212', '-');
        Matcher matcher = NUMBER_WITH_UNIT.matcher(normalizedQuery);
        while (matcher.find()) {
            UnitNormalizer.NormalizedQuantity normalized = units.normalize(BigDecimal.ONE, matcher.group(2));
            if (normalized.knownUnit()) queryUnits.add(unitKey(normalized.normalizedUnit()));
        }
        if (queryUnits.isEmpty()) return 0;
        Set<String> allowed = new LinkedHashSet<>();
        for (QuantityProjection quantity : concat(contract.requiredQuantities(), contract.optionalQuantities())) {
            for (String unit : quantity.acceptedInputUnits()) {
                UnitNormalizer.NormalizedQuantity normalized = units.normalize(BigDecimal.ONE, unit);
                allowed.add(unitKey(normalized.knownUnit() ? normalized.normalizedUnit() : unit));
            }
        }
        long compatible = queryUnits.stream().filter(allowed::contains).count();
        return (double) compatible / queryUnits.size();
    }

    /** Scores only numeric facts that can be uniquely tied to a declared quantity mention. */
    private double contractContradiction(String query, CandidateContractProjection contract) {
        String normalizedQuery = Normalizer.normalize(query, Normalizer.Form.NFKC).replace('\u2212', '-');
        List<ObservedQuantity> observations = observedQuantities(normalizedQuery, contract);
        if (observations.isEmpty()) return 0;
        Set<ObservedQuantity> violations = new HashSet<>(sameUnitViolations(observations, contract));
        for (ObservedQuantity observation : observations) {
            UnitNormalizer.NormalizedQuantity normalized = observation.normalized();
            QuantityProjection quantity = observation.quantity();
            if (!allowsNormalizedUnit(quantity, normalized.normalizedUnit())
                    || violates(quantity, normalized.normalizedValue())) {
                violations.add(observation);
            }
        }
        return (double) violations.size() / observations.size();
    }

    private List<ObservedQuantity> observedQuantities(String query, CandidateContractProjection contract) {
        Matcher values = NUMBER_WITH_UNIT.matcher(query);
        List<ObservedQuantity> result = new ArrayList<>();
        List<QuantityProjection> quantities = concat(contract.requiredQuantities(), contract.optionalQuantities());
        while (values.find()) {
            BigDecimal value;
            try {
                value = new BigDecimal(values.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                continue;
            }
            UnitNormalizer.NormalizedQuantity normalized = units.normalize(value, values.group(2));
            if (!normalized.knownUnit()) continue;
            int unitStart = values.start(2);
            int unitEnd = values.end(2);
            Set<QuantityProjection> explicitlyLinked = explicitLinks(query, values.start(1), quantities);
            if (explicitlyLinked.size() == 1) {
                result.add(new ObservedQuantity(explicitlyLinked.iterator().next(), normalized));
                continue;
            }
            if (explicitlyLinked.size() > 1) continue;
            QuantityProjection nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            boolean ambiguous = false;
            for (QuantityProjection quantity : quantities) {
                for (IdentifierMention mention : mentions(query, quantity, unitStart, unitEnd)) {
                    int distance = distance(values.start(1), unitEnd, mention.start(), mention.end());
                    if (distance > properties.quantityAssociationWindowCharacters()) continue;
                    if (distance < nearestDistance) {
                        nearest = quantity;
                        nearestDistance = distance;
                        ambiguous = false;
                    } else if (distance == nearestDistance && nearest != quantity) {
                        ambiguous = true;
                    }
                }
            }
            if (nearest != null && !ambiguous) result.add(new ObservedQuantity(nearest, normalized));
        }
        return result;
    }

    /** Explicit key/value syntax remains useful when prose distance exceeds the proximity window. */
    private Set<QuantityProjection> explicitLinks(String query, int valueStart,
            List<QuantityProjection> quantities) {
        Set<QuantityProjection> linked = new LinkedHashSet<>();
        for (QuantityProjection quantity : quantities) {
            for (IdentifierMention mention : mentions(query, quantity, valueStart, valueStart)) {
                if (mention.end() > valueStart) continue;
                String connector = query.substring(mention.end(), valueStart);
                if (EXPLICIT_VALUE_LINK.matcher(connector).matches()) linked.add(quantity);
            }
        }
        return linked;
    }

    /** Enforces declared sameUnitAs relations when both sides are explicitly observed. */
    private Set<ObservedQuantity> sameUnitViolations(List<ObservedQuantity> observations,
            CandidateContractProjection contract) {
        java.util.Map<String, List<ObservedQuantity>> byKey = new java.util.HashMap<>();
        for (ObservedQuantity observation : observations) {
            byKey.computeIfAbsent(observation.quantity().key(), ignored -> new ArrayList<>()).add(observation);
        }
        Set<ObservedQuantity> violations = new HashSet<>();
        for (QuantityProjection quantity : concat(contract.requiredQuantities(), contract.optionalQuantities())) {
            Object relation = quantity.constraints().get("sameUnitAs");
            if (!(relation instanceof String reference) || reference.isBlank()) continue;
            List<ObservedQuantity> subjectValues = byKey.getOrDefault(quantity.key(), List.of());
            List<ObservedQuantity> referenceValues = byKey.getOrDefault(reference.trim(), List.of());
            // Multiple observations on either side cannot be paired reliably without a typed relation
            // payload, so leave those cases to backend validation instead of guessing.
            if (subjectValues.size() != 1 || referenceValues.size() != 1) continue;
            ObservedQuantity subject = subjectValues.getFirst();
            ObservedQuantity target = referenceValues.getFirst();
            if (!unitKey(subject.normalized().normalizedUnit()).equals(
                    unitKey(target.normalized().normalizedUnit()))) {
                violations.add(subject);
                violations.add(target);
            }
        }
        return violations;
    }

    private List<IdentifierMention> mentions(String query, QuantityProjection quantity, int unitStart, int unitEnd) {
        List<IdentifierMention> result = new ArrayList<>();
        addMentions(result, query, quantity.key(), isSingleSymbol(quantity.key()), unitStart, unitEnd);
        for (String alias : quantity.aliases()) {
            addMentions(result, query, alias, isSingleSymbol(alias), unitStart, unitEnd);
        }
        for (String symbol : quantity.symbols()) {
            addMentions(result, query, symbol, true, unitStart, unitEnd);
        }
        return result;
    }

    private void addMentions(List<IdentifierMention> result, String query, String identifier, boolean exactCase,
            int unitStart, int unitEnd) {
        if (identifier == null || identifier.isBlank()) return;
        int flags = exactCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(identifier.trim())
                + "(?![\\p{L}\\p{N}_])", flags);
        Matcher matcher = pattern.matcher(query);
        while (matcher.find()) {
            if (matcher.start() < unitEnd && matcher.end() > unitStart) continue;
            result.add(new IdentifierMention(matcher.start(), matcher.end()));
        }
    }

    private boolean isSingleSymbol(String identifier) {
        return identifier.codePointCount(0, identifier.length()) == 1;
    }

    private int distance(int valueStart, int unitEnd, int mentionStart, int mentionEnd) {
        if (mentionEnd <= valueStart) return valueStart - mentionEnd;
        if (mentionStart >= unitEnd) return mentionStart - unitEnd;
        return 0;
    }

    private boolean allowsNormalizedUnit(QuantityProjection quantity, String normalizedUnit) {
        return quantity.acceptedInputUnits().stream().anyMatch(unit -> {
            UnitNormalizer.NormalizedQuantity normalized = units.normalize(BigDecimal.ONE, unit);
            String accepted = normalized.knownUnit() ? normalized.normalizedUnit() : unit;
            return unitKey(accepted).equals(unitKey(normalizedUnit));
        });
    }

    private boolean violates(QuantityProjection quantity, BigDecimal value) {
        var constraints = quantity.constraints();
        if (Boolean.TRUE.equals(constraints.get("positive")) && value.signum() <= 0) return true;
        if (Boolean.TRUE.equals(constraints.get("nonNegative")) && value.signum() < 0) return true;
        if (Boolean.TRUE.equals(constraints.get("integer")) && value.stripTrailingZeros().scale() > 0) return true;
        BigDecimal minimum = firstDecimal(constraints, "min", "minimum", "minInclusive");
        BigDecimal maximum = firstDecimal(constraints, "max", "maximum", "maxInclusive");
        return minimum != null && value.compareTo(minimum) < 0
                || maximum != null && value.compareTo(maximum) > 0;
    }

    private BigDecimal firstDecimal(java.util.Map<String, Object> constraints, String... keys) {
        for (String key : keys) {
            Object value = constraints.get(key);
            if (value instanceof BigDecimal decimal) return decimal;
            if (value instanceof Number number) return new BigDecimal(number.toString());
        }
        return null;
    }

    private double metadataOverlap(Set<String> queryTerms, String searchText) {
        if (queryTerms.isEmpty()) return 0;
        Set<String> documentTerms = new HashSet<>(tokenizer.tokenize(searchText));
        long matches = queryTerms.stream().filter(documentTerms::contains).count();
        return (double) matches / queryTerms.size();
    }

    private boolean matches(Set<String> queryTerms, String key, List<String> aliases, List<String> symbols) {
        if (tokenizer.tokenize(key).stream().anyMatch(queryTerms::contains)) return true;
        if (aliases.stream().flatMap(alias -> tokenizer.tokenize(alias).stream()).anyMatch(queryTerms::contains)) return true;
        return symbols.stream().flatMap(symbol -> tokenizer.tokenize(symbol).stream()).anyMatch(queryTerms::contains);
    }

    private List<QuantityProjection> concat(List<QuantityProjection> left, List<QuantityProjection> right) {
        List<QuantityProjection> result = new ArrayList<>(left);
        result.addAll(right);
        return result;
    }

    private String unitKey(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    public record Result(double confidence, VerificationEvidence evidence) {
        public Result {
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("confidence must be in [0, 1]");
            }
        }
    }

    private record IdentifierMention(int start, int end) { }
    private record ObservedQuantity(QuantityProjection quantity, UnitNormalizer.NormalizedQuantity normalized) { }
}
