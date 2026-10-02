package com.example.backend.system.realtime.dto;

import java.time.Instant;

/** JSON view sent through the community and classroom event stream. */
public record RealtimeEvent(long revision, String path, String clientId, Instant occurredAt) {
    /** A business change awaiting HTTP event-stream metadata. */
    public record Change(String path, String clientId) { }
}
