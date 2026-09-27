package com.example.backend.dto.problem;

import java.util.Map;

/** Request shape retained for clients that receive the explicit legacy-gone response. */
public record ConfirmProblemRequest(Map<String, String> answers) { }
