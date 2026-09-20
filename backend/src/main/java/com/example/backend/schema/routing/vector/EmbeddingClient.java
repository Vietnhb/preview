package com.example.backend.schema.routing.vector;

public interface EmbeddingClient {
    EmbeddingResult embed(String text);
    String providerId();
    String modelId();
    int dimension();
}
