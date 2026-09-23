package com.example.backend.ai.extraction.model;

public record ConversationTurn(String role, String text) {
    public ConversationTurn {
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new IllegalArgumentException("Conversation role must be user or assistant.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Conversation text must not be blank.");
        }
    }
}
