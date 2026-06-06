package com.novel2script.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Utility to safely send any Java object as JSON via {@link SseEmitter}.
 *
 * <h3>Why this exists</h3>
 * {@code SseEmitter.send(SseEmitter.event().data(obj))} fails when
 * {@code obj} is not a {@link String}, because Spring's default
 * {@code HttpMessageConverter} chain for {@code text/event-stream}
 * only handles String payloads out-of-the-box.
 *
 * <p>This utility pre-serializes the object to a JSON string using
 * the application's configured {@link ObjectMapper} (which includes
 * JavaTimeModule, etc.), guaranteeing that the SSE emitter always
 * receives a String it can handle.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Before (❌ fails for LinkedHashSet, Map, etc.):
 *   emitter.send(SseEmitter.event().name("data").data(myLinkedHashSet));
 *
 *   // After (✅ always works):
 *   sseEmitterUtils.send(emitter, "data", myLinkedHashSet);
 *
 *   // Or build a custom event:
 *   sseEmitterUtils.send(emitter,
 *       SseEmitter.event().id("123").name("progress"),
 *       myProgressObject);
 * }</pre>
 */
@Slf4j
@Component
public class SseEmitterUtils {

    private final ObjectMapper objectMapper;

    public SseEmitterUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Send an object as a named SSE event with auto-generated ID.
     *
     * @param emitter    the SSE emitter
     * @param eventName  SSE event name (e.g. "progress", "character", "scene")
     * @param data       any Serializable object — will be converted to JSON
     */
    public void send(SseEmitter emitter, String eventName, Object data) {
        send(emitter,
                SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name(eventName),
                data);
    }

    /**
     * Send an object using a pre-configured {@link SseEmitter.SseEventBuilder}.
     * The data object is serialized to JSON before being passed to the builder.
     *
     * <p>IOException (client disconnect) is logged at DEBUG level —
     * it's normal lifecycle, not an application error.
     *
     * @param emitter      the SSE emitter
     * @param eventBuilder the event builder (without .data() called yet)
     * @param data         any Serializable object
     */
    public void send(SseEmitter emitter, SseEmitter.SseEventBuilder eventBuilder, Object data) {
        String json = toJson(data);
        if (json == null) {
            log.warn("SseEmitterUtils: failed to serialize {} — skipping event", data);
            return;
        }
        try {
            emitter.send(eventBuilder.data(json));
        } catch (java.io.IOException e) {
            // Client disconnected — normal SSE lifecycle, not an error
            log.debug("SseEmitterUtils: client disconnected during send — {}", e.getMessage());
        } catch (Exception e) {
            log.warn("SseEmitterUtils: send failed unexpectedly: {}", e.getMessage());
        }
    }

    /**
     * Send a raw string directly (bypasses JSON serialization).
     * Only use this when the data is already a valid SSE-formatted string.
     */
    public void sendRaw(SseEmitter emitter, SseEmitter.SseEventBuilder eventBuilder, String rawData) {
        try {
            emitter.send(eventBuilder.data(rawData));
        } catch (java.io.IOException e) {
            log.debug("SseEmitterUtils: client disconnected during raw send — {}", e.getMessage());
        } catch (Exception e) {
            log.warn("SseEmitterUtils: raw send failed unexpectedly: {}", e.getMessage());
        }
    }

    /**
     * Convert any object to its JSON string representation.
     * Returns {@code null} if serialization fails.
     */
    public String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("SseEmitterUtils: JSON serialization failed for {}: {}",
                    obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Create an SSE heartbeat/comment event to keep the connection alive.
     * SSE comments (lines starting with ":") are ignored by browsers
     * but prevent proxy/server timeouts.
     */
    public void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (Exception e) {
            log.debug("Heartbeat send failed (client may have disconnected)");
        }
    }
}
