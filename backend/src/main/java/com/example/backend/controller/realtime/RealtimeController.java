package com.example.backend.controller.realtime;

import com.example.backend.service.realtime.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
public class RealtimeController {
    private final RealtimeEventService realtime;

    @GetMapping(path = "/events", produces = "text/event-stream")
    public ResponseEntity<SseEmitter> events() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Accel-Buffering", "no")
            .body(realtime.connect());
    }
}
