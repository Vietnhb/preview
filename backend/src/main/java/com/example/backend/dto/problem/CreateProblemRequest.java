package com.example.backend.dto.problem;

import java.util.UUID;

import com.example.backend.entity.SourceMode;

public record CreateProblemRequest(SourceMode sourceMode, String text, UUID lessonId) {
}
