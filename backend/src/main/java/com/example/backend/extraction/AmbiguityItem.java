package com.example.backend.extraction;

import java.util.List;

public record AmbiguityItem(
        String code,
        String fieldPath,
        String question,
        List<String> options) {
}
