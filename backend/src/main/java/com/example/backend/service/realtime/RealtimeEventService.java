package com.example.backend.service.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RealtimeEventService {
    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;
    private final AtomicLong revision = new AtomicLong();
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public record Change(long revision, String path, String clientId, Instant occurredAt) { }

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data(revision.get()).reconnectTime(1_000));
        } catch (IOException ex) {
            remove.run();
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void publish(String path, String clientId) {
        Change change = new Change(revision.incrementAndGet(), path, clientId, Instant.now());
        broadcast(SseEmitter.event().id(Long.toString(change.revision())).name("change").data(change));
    }

    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        broadcast(SseEmitter.event().name("heartbeat").data(Instant.now().toString()));
    }

    private void broadcast(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }
}
