package com.novel2script.api.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Thread-safe registry of active {@link SseEmitter} instances keyed by script ID.
 *
 * <h3>Purpose</h3>
 * Bridges the gap between the request-scoped SSE controller (which holds the
 * {@link SseEmitter}) and the singleton-scoped {@code GenerationOrchestrator}
 * (which produces progress updates). Instead of polling shared state every
 * 2 seconds, the orchestrator <em>pushes</em> events through this registry
 * and every registered emitter receives them instantly.
 *
 * <h3>Thread safety</h3>
 * <ul>
 *   <li>{@link ConcurrentHashMap} guards the outer map.</li>
 *   <li>{@link CopyOnWriteArraySet} guards each per-script emitter set —
 *       iteration during {@link #send} is safe even when a disconnected
 *       emitter is concurrently removed via {@link #unregister}.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Controller calls {@link #register} when a client connects.</li>
 *   <li>{@code ScriptService} calls {@link #send} on every progress update.</li>
 *   <li>{@code ScriptService} calls {@link #closeAll} when the script reaches
 *       a terminal state ({@code COMPLETED} or {@code FAILED}).</li>
 *   <li>Controller calls {@link #unregister} on timeout / disconnect / error.</li>
 * </ol>
 */
@Slf4j
@Component
public class SseEmitterRegistry {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<SseEmitter>> emitters
            = new ConcurrentHashMap<>();

    private final SseEmitterUtils sseUtils;

    public SseEmitterRegistry(SseEmitterUtils sseUtils) {
        this.sseUtils = sseUtils;
    }

    /**
     * Register an emitter to receive push events for the given script.
     * Idempotent — calling multiple times with the same emitter is safe.
     */
    public void register(Long scriptId, SseEmitter emitter) {
        CopyOnWriteArraySet<SseEmitter> set = emitters
                .computeIfAbsent(scriptId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        log.debug("SSE emitter registered: scriptId={}, totalEmitters={}", scriptId, set.size());
    }

    /**
     * Remove an emitter from the registry.
     * Called on client disconnect, timeout, or completion.
     */
    public void unregister(Long scriptId, SseEmitter emitter) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.get(scriptId);
        if (set == null) return;
        set.remove(emitter);
        log.debug("SSE emitter unregistered: scriptId={}, remaining={}", scriptId, set.size());
    }

    /**
     * Push a named SSE event to all emitters registered for the given script.
     * If no emitters are registered, this is a silent no-op.
     *
     * <p>Dead emitters (whose {@code send} throws {@link IOException}) are
     * automatically removed from the registry.
     *
     * @param scriptId  the script whose emitters should receive the event
     * @param eventName SSE event name (e.g. "progress", "complete", "error")
     * @param data      any serializable object — converted to JSON via {@link SseEmitterUtils}
     */
    public void send(Long scriptId, String eventName, Object data) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.get(scriptId);
        if (set == null || set.isEmpty()) return;

        String json = sseUtils.toJson(data);
        if (json == null) {
            log.warn("SseEmitterRegistry: failed to serialize event '{}' for scriptId={}", eventName, scriptId);
            return;
        }

        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name(eventName)
                        .data(json));
            } catch (IOException e) {
                // Client disconnected — normal SSE lifecycle
                log.debug("SseEmitterRegistry: client disconnected for scriptId={} — removing", scriptId);
                unregister(scriptId, emitter);
            }
        }
    }

    /**
     * Complete all emitters for a script and remove them from the registry.
     * Called when the script reaches a terminal state (COMPLETED or FAILED).
     */
    public void closeAll(Long scriptId) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.remove(scriptId);
        if (set == null || set.isEmpty()) return;

        log.debug("SseEmitterRegistry: closing {} emitter(s) for scriptId={}", set.size(), scriptId);
        for (SseEmitter emitter : set) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // emitter may already be closed
            }
        }
    }

    /**
     * Check if there are any active listeners for a script.
     */
    public boolean hasListeners(Long scriptId) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.get(scriptId);
        return set != null && !set.isEmpty();
    }
}
