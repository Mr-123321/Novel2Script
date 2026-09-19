package com.novel2script.api.listener;

import com.novel2script.api.util.SseEmitterRegistry;
import com.novel2script.application.service.ScriptService;
import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.common.enums.WorkflowStep;
import com.novel2script.domain.dto.GenerationProgress;
import com.novel2script.domain.event.ScriptProgressChangedEvent;
import com.novel2script.domain.model.Script;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges {@link ScriptProgressChangedEvent} (from the application layer)
 * to SSE clients (in the API layer).
 *
 * <p>When the generation orchestrator updates script progress, the
 * {@link ScriptService} fires a {@link ScriptProgressChangedEvent}.
 * This listener picks it up, reads the current script state, builds a
 * {@link GenerationProgress}, and pushes it through the
 * {@link SseEmitterRegistry} to all connected SSE clients.
 *
 * <p>This decoupling keeps the {@code novel2script-application} module
 * free of any web/SSE dependencies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseProgressListener {

    private final ScriptService scriptService;
    private final SseEmitterRegistry sseRegistry;

    @EventListener
    public void onScriptProgressChanged(ScriptProgressChangedEvent event) {
        long scriptId = event.scriptId();

        Optional<Script> scriptOpt = scriptService.findByIdQuietly(scriptId);
        if (scriptOpt.isEmpty()) {
            log.debug("SseProgressListener: script {} not found — skipping", scriptId);
            return;
        }

        Script script = scriptOpt.get();
        ScriptStatus status = script.getStatus();

        if (status == ScriptStatus.COMPLETED || status == ScriptStatus.COMPLETED_WITH_WARNINGS) {
            // Send final progress snapshot, then complete event, then close all connections.
            // COMPLETED_WITH_WARNINGS = finished, but some scenes were left empty (待补全)
            // instead of being padded with fabricated content.
            sendProgress(script);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("scriptId", scriptId);
            payload.put("status", status.name());
            Map<String, Object> ws = script.getWorkflowState();
            if (ws != null) {
                if (ws.get("failedDialogueScenes") != null) {
                    payload.put("failedDialogueScenes", ws.get("failedDialogueScenes"));
                }
                if (ws.get("failedActionScenes") != null) {
                    payload.put("failedActionScenes", ws.get("failedActionScenes"));
                }
            }
            sseRegistry.send(scriptId, "complete", payload);
            sseRegistry.closeAll(scriptId);
            log.debug("SseProgressListener: script {} {} — SSE connections closed", scriptId, status);
            return;
        }

        if (status == ScriptStatus.FAILED) {
            String errorMsg = "剧本生成失败，请重试";
            Map<String, Object> ws = script.getWorkflowState();
            if (ws != null && ws.get("error") instanceof String err && !err.isBlank()) {
                errorMsg = err;
            }
            sseRegistry.send(scriptId, "error",
                    Map.of("scriptId", scriptId, "status", "FAILED", "message", errorMsg));
            sseRegistry.closeAll(scriptId);
            log.debug("SseProgressListener: script {} FAILED — SSE connections closed", scriptId);
            return;
        }

        // Still GENERATING — push progress
        sendProgress(script);
    }

    private void sendProgress(Script script) {
        Long scriptId = script.getId();

        WorkflowStep currentStep = WorkflowStep.CHAPTER_PARSE;
        Map<String, Object> ws = script.getWorkflowState();
        if (ws != null && ws.get("currentStep") instanceof String stepName) {
            try {
                currentStep = WorkflowStep.valueOf(stepName);
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }

        double progress = script.getProgress();
        GenerationProgress gp = new GenerationProgress(
                String.valueOf(scriptId),
                currentStep,
                progress,
                script.getStatus() != null ? script.getStatus().name() : "UNKNOWN",
                Instant.now(),
                progress >= 100.0 ? Instant.now() : Instant.now().plusSeconds(300),
                script.getTitle() != null ? script.getTitle() : "Generating..."
        );

        sseRegistry.send(scriptId, "progress", gp);
    }
}
