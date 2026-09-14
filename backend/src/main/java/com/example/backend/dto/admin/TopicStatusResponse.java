package com.example.backend.dto.admin;

import java.util.UUID;

public record TopicStatusResponse(UUID id, String name, boolean enabled) {
}
