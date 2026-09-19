package com.example.backend.ai.extraction.model;

import java.util.List;

public record AmbiguityItem(
        String code,
        String fieldPath,
        String question,
        List<String> options) {
}
