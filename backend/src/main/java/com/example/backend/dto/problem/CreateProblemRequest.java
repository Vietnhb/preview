package com.example.backend.dto.problem;

import java.util.UUID;

import com.example.backend.enums.SourceMode;

public record CreateProblemRequest(SourceMode sourceMode, String text, UUID lessonId) {
}
