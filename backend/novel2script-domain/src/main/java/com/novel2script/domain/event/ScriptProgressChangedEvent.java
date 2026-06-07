package com.novel2script.domain.event;

/**
 * Domain event fired when a script's generation state changes
 * (progress updated, completed, or failed).
 *
 * <p>SSE listeners in the API layer subscribe to this event and
 * push progress updates to connected clients via {@code SseEmitterRegistry}.
 * This decouples the application layer (which owns generation logic) from
 * the API layer (which owns SSE transport).
 */
public record ScriptProgressChangedEvent(
        long scriptId
) {
    @Override
    public String toString() {
        return "ScriptProgressChangedEvent[scriptId=" + scriptId + "]";
    }
}
