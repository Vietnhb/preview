package com.example.backend.dto.problem;

import java.util.Map;

public record ConfirmProblemRequest(Map<String, String> answers) {
}
