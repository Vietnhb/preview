package com.example.backend.schema.routing.lexical;

import com.example.backend.schema.routing.model.SchemaSearchDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** BM25 ranker that scores requests against an immutable, precomputed catalog index. */
public final class Bm25SchemaRetriever {
    private static final double MAX_K1 = 10.0;

    private final double k1;
    private final double b;
    private final UnicodePhysicsTokenizer tokenizer;

    public Bm25SchemaRetriever(double k1, double b) {
        this(k1, b, new UnicodePhysicsTokenizer());
    }

    public Bm25SchemaRetriever(double k1, double b, UnicodePhysicsTokenizer tokenizer) {
        if (!Double.isFinite(k1) || k1 <= 0.0 || k1 > MAX_K1) {
            throw new IllegalArgumentException("k1 must be finite and in (0, 10]");
        }
        if (!Double.isFinite(b) || b < 0.0 || b > 1.0) {
            throw new IllegalArgumentException("b must be finite and in [0, 1]");
        }
        this.k1 = k1;
        this.b = b;
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
    }

    /** Convenience method that rebuilds the index; request paths should reuse {@link Index}. */
    public List<RankedDocument> rank(
            String query,
            Collection<SchemaSearchDocument> corpus,
            int topK) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(corpus, "corpus");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1");
        }
        return rank(query, buildIndex(corpus), topK);
    }

    /** Builds a deterministic immutable snapshot for reuse across query requests. */
    public Index buildIndex(Collection<SchemaSearchDocument> corpus) {
        Objects.requireNonNull(corpus, "corpus");
        List<DocumentTerms> documents = new ArrayList<>(snapshot(corpus));
        documents.sort(Comparator.comparing((DocumentTerms document) -> document.document.schemaId())
                .thenComparing(document -> document.document.schemaVersion()));
        Map<String, Integer> documentFrequency = documentFrequencies(documents);
        double averageDocumentLength = documents.stream()
                .mapToInt(document -> document.length)
                .average()
                .orElse(0.0);
        return new Index(documents, documentFrequency, averageDocumentLength);
    }

    /** Scores a query using only precomputed data from the supplied immutable index. */
    public List<RankedDocument> rank(String query, Index index, int topK) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(index, "index");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1");
        }

        List<String> queryTokens = retrievalTokens(query);
        if (queryTokens.isEmpty() || index.documents.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueQueryTerms = new LinkedHashSet<>(queryTokens);
        List<ScoredDocument> ranked = new ArrayList<>();
        for (DocumentTerms document : index.documents) {
            double score = 0.0;
            for (String term : uniqueQueryTerms) {
                int termFrequency = document.termFrequencies.getOrDefault(term, 0);
                if (termFrequency == 0) {
                    continue;
                }

                int frequency = index.documentFrequency.getOrDefault(term, 0);
                double inverseDocumentFrequency = Math.log1p(
                        (index.documents.size() - frequency + 0.5) / (frequency + 0.5));
                double lengthNormalization = 1.0 - b
                        + b * document.length / index.averageDocumentLength;
                double termScore = inverseDocumentFrequency
                        * (termFrequency * (k1 + 1.0))
                        / (termFrequency + k1 * lengthNormalization);
                score += termScore;
            }
            if (score > 0.0) {
                ranked.add(new ScoredDocument(document.document, score));
            }
        }

        ranked.sort(Comparator.comparingDouble(ScoredDocument::score).reversed()
                .thenComparing(item -> item.document().schemaId())
                .thenComparing(item -> item.document().schemaVersion()));
        int resultSize = Math.min(topK, ranked.size());
        List<RankedDocument> result = new ArrayList<>(resultSize);
        for (int resultIndex = 0; resultIndex < resultSize; resultIndex++) {
            ScoredDocument item = ranked.get(resultIndex);
            result.add(new RankedDocument(item.document(), resultIndex + 1, item.score()));
        }
        return List.copyOf(result);
    }

    private List<DocumentTerms> snapshot(Collection<SchemaSearchDocument> corpus) {
        List<DocumentTerms> documents = new ArrayList<>(corpus.size());
        Set<DocumentIdentity> identities = new HashSet<>();
        for (SchemaSearchDocument document : corpus) {
            Objects.requireNonNull(document, "corpus must not contain null");
            DocumentIdentity identity = new DocumentIdentity(document.schemaId(), document.schemaVersion());
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("duplicate schema ID/version in corpus: "
                        + document.schemaId() + "@" + document.schemaVersion());
            }
            List<String> terms = retrievalTokens(document.searchText());
            Map<String, Integer> frequencies = new TreeMap<>();
            for (String term : terms) {
                frequencies.merge(term, 1, Integer::sum);
            }
            int length = frequencies.values().stream().mapToInt(Integer::intValue).sum();
            documents.add(new DocumentTerms(document, frequencies, length));
        }
        return List.copyOf(documents);
    }

    private List<String> retrievalTokens(String text) {
        return tokenizer.tokenizeWithAccentShadow(text).stream()
                .filter(token -> !UnicodePhysicsStopWords.isStopWord(token))
                .toList();
    }

    private static Map<String, Integer> documentFrequencies(List<DocumentTerms> documents) {
        Map<String, Integer> frequencies = new TreeMap<>();
        for (DocumentTerms document : documents) {
            for (String term : document.termFrequencies.keySet()) {
                frequencies.merge(term, 1, Integer::sum);
            }
        }
        return Collections.unmodifiableMap(frequencies);
    }

    /** Immutable, deterministic BM25 corpus statistics and term-frequency snapshot. */
    public static final class Index {
        private final List<DocumentTerms> documents;
        private final Map<String, Integer> documentFrequency;
        private final double averageDocumentLength;

        private Index(
                Collection<DocumentTerms> documents,
                Map<String, Integer> documentFrequency,
                double averageDocumentLength) {
            this.documents = List.copyOf(documents);
            this.documentFrequency = Collections.unmodifiableMap(new TreeMap<>(documentFrequency));
            if (!Double.isFinite(averageDocumentLength) || averageDocumentLength < 0.0) {
                throw new IllegalArgumentException("averageDocumentLength must be finite and non-negative");
            }
            this.averageDocumentLength = averageDocumentLength;
        }

        public int documentCount() {
            return documents.size();
        }

        public double averageDocumentLength() {
            return averageDocumentLength;
        }
    }

    public record RankedDocument(SchemaSearchDocument document, int rank, double score) {
        public RankedDocument {
            Objects.requireNonNull(document, "document");
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be at least 1");
            }
            if (!Double.isFinite(score) || score <= 0.0) {
                throw new IllegalArgumentException("score must be finite and positive");
            }
        }
    }

    private record DocumentTerms(
            SchemaSearchDocument document,
            Map<String, Integer> termFrequencies,
            int length) {
        private DocumentTerms {
            termFrequencies = Collections.unmodifiableMap(new TreeMap<>(termFrequencies));
        }
    }

    private record ScoredDocument(SchemaSearchDocument document, double score) {
    }

    private record DocumentIdentity(String schemaId, String schemaVersion) {
    }
}
