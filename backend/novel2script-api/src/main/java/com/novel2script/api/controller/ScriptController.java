package com.novel2script.api.controller;

import com.novel2script.api.util.SseEmitterUtils;
import com.novel2script.application.service.NovelService;
import com.novel2script.application.service.ScriptService;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.WorkflowStep;
import com.novel2script.domain.dto.GenerationProgress;
import com.novel2script.domain.dto.ScriptGenerateRequest;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.PlotInsertion;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REST controller for script generation and management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scripts")
@RequiredArgsConstructor
@Tag(name = "剧本管理", description = "AI 剧本生成与管理 API")
public class ScriptController {

    private final ScriptService scriptService;
    private final NovelService novelService;
    private final SseEmitterUtils sseUtils;

    @PostMapping("/generate")
    @Operation(summary = "发起剧本生成任务，返回执行 ID 用于跟踪进度")
    public ResponseEntity<Map<String, Object>> generate(
            @Valid @RequestBody ScriptGenerateRequest request) {

        log.info("Script generation requested: novelId={}, maxScenes={}, style='{}'",
                request.novelId(), request.maxScenes(), request.style());

        List<String> focusCharList = request.focusCharacters() != null
                ? Arrays.stream(request.focusCharacters().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList()
                : List.of();

        Script script = scriptService.generateScript(
                request.novelId(),
                request.maxScenes(),
                request.style(),
                focusCharList);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", String.valueOf(script.getId()));
        response.put("status", script.getStatus() != null ? script.getStatus().name() : "DRAFT");
        response.put("message", "剧本生成任务已提交");

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping
    @Operation(summary = "列出所有剧本")
    public ResponseEntity<List<Script>> listAll() {
        List<Script> scripts = scriptService.listAll();
        return ResponseEntity.ok(scripts);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 获取剧本详情")
    public ResponseEntity<Object> getById(@PathVariable Long id) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Map<String, Object> notFound = new LinkedHashMap<>();
                    notFound.put("code", 404);
                    notFound.put("message", "剧本不存在: id=" + id);
                    return ResponseEntity.status(404).body(notFound);
                });
    }

    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 实时推送剧本生成进度")
    public SseEmitter streamProgress(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
        AtomicBoolean completed = new AtomicBoolean(false);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Graceful cleanup on any terminal event
        Runnable cleanup = () -> {
            if (completed.compareAndSet(false, true)) {
                scheduler.shutdownNow();
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            log.info("SSE timeout for script id={}", id);
            cleanup.run();
        });
        emitter.onError(e -> {
            log.warn("SSE callback error for script id={}: {}", id,
                    e != null ? e.getMessage() : "null");
            cleanup.run();
        });

        scheduler.scheduleAtFixedRate(() -> {
            // Don't push if emitter is already closed
            if (completed.get()) return;

            try {
                double progress = scriptService.getProgress(id);
                Optional<Script> scriptOpt = scriptService.findByIdQuietly(id);

                // Derive current step from workflow state (or fallback to CHAPTER_PARSE)
                WorkflowStep currentStep = WorkflowStep.CHAPTER_PARSE;
                if (scriptOpt.isPresent()) {
                    Map<String, Object> ws = scriptOpt.get().getWorkflowState();
                    if (ws != null && ws.get("currentStep") instanceof String stepName) {
                        try {
                            currentStep = WorkflowStep.valueOf(stepName);
                        } catch (IllegalArgumentException ignored) {
                            // keep default
                        }
                    }
                }

                GenerationProgress gp = new GenerationProgress(
                        String.valueOf(id),
                        currentStep,
                        progress,
                        scriptOpt.map(s -> s.getStatus() != null ? s.getStatus().name() : "UNKNOWN")
                                .orElse("UNKNOWN"),
                        Instant.now(),
                        progress >= 100.0 ? Instant.now() : Instant.now().plusSeconds(300),
                        scriptOpt.map(Script::getTitle).orElse("Generating...")
                );

                // ✅ Uses SseEmitterUtils — safely serializes any object to JSON
                sseUtils.send(emitter, "progress", gp);

                // Check for FAILED status — send error event and stop
                if (scriptOpt.isPresent() && scriptOpt.get().getStatus() != null
                        && scriptOpt.get().getStatus().name().equals("FAILED")) {
                    String errorMsg = "剧本生成失败，请重试";
                    Map<String, Object> ws = scriptOpt.get().getWorkflowState();
                    if (ws != null && ws.get("error") instanceof String err) {
                        errorMsg = err;
                    }
                    // ✅ Uses SseEmitterUtils — safely serializes Map to JSON
                    sseUtils.send(emitter,
                            SseEmitter.event().name("error"),
                            Map.of("scriptId", id, "status", "FAILED",
                                    "message", errorMsg));
                    emitter.complete();
                    cleanup.run();
                    return;
                }

                if (progress >= 100.0) {
                    sseUtils.send(emitter,
                            SseEmitter.event().name("complete"),
                            Map.of("scriptId", id, "status", "COMPLETED"));
                    emitter.complete();
                    cleanup.run();
                }
            } catch (Exception e) {
                // Client disconnect or send failure — log and stop
                if (e instanceof IOException) {
                    log.info("SSE client disconnected for script id={}", id);
                } else {
                    log.warn("SSE send error for script id={}: {}", id, e.getMessage());
                }
                cleanup.run();
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // emitter may already be closed
                }
            }
        }, 0, 2, TimeUnit.SECONDS);

        return emitter;
    }

    @GetMapping("/{id}/workflow/mermaid")
    @Operation(summary = "获取剧本生成工作流的 Mermaid 流程图")
    public ResponseEntity<Map<String, String>> getWorkflowMermaid(@PathVariable Long id) {
        // Placeholder — in production this would be built from the actual workflow state
        String mermaid = """
                graph TD
                    A[章节解析 ChapterParser] --> B[角色提取 CharacterExtract]
                    B --> C[角色消歧 CharacterResolve]
                    C --> D[情节提取 PlotExtract]
                    D --> E[场景切分 SceneSegment]
                    E --> F[对白生成 DialogueGen]
                    F --> G[动作生成 ActionGen]
                    G --> H[剧本合成 ScriptCompose]
                    H --> I[YAML 导出 YamlExport]
                    I --> J[分镜生成 Storyboard]
                """;

        Map<String, String> response = new LinkedHashMap<>();
        response.put("scriptId", String.valueOf(id));
        response.put("mermaid", mermaid);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/scenes/{sceneId}")
    @Operation(summary = "更新剧本中指定场景的内容")
    public ResponseEntity<Map<String, Object>> updateScene(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @RequestBody Map<String, Object> updates) {

        log.info("Update scene: scriptId={}, sceneId={}", id, sceneId);

        // Placeholder — in production this would delegate to ScriptService
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scriptId", id);
        response.put("sceneId", sceneId);
        response.put("updated", true);
        response.put("message", "场景更新已接收（功能开发中）");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/dialogues/{dialogueId}")
    @Operation(summary = "更新剧本中指定对话的内容")
    public ResponseEntity<Map<String, Object>> updateDialogue(
            @PathVariable Long id,
            @PathVariable Long dialogueId,
            @RequestBody Map<String, Object> updates) {

        log.info("Update dialogue: scriptId={}, dialogueId={}", id, dialogueId);

        // Placeholder — in production this would delegate to ScriptService
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scriptId", id);
        response.put("dialogueId", dialogueId);
        response.put("updated", true);
        response.put("message", "对话更新已接收（功能开发中）");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/characters/{characterId}")
    @Operation(summary = "更新剧本中指定角色的信息")
    public ResponseEntity<Object> updateCharacter(
            @PathVariable Long id,
            @PathVariable Long characterId,
            @RequestBody Map<String, Object> updates) {

        log.info("Update character: scriptId={}, characterId={}", id, characterId);

        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    try {
                        scriptService.updateCharacter(id, characterId, updates);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("scriptId", id);
                    response.put("characterId", characterId);
                    response.put("updated", true);
                    response.put("message", "角色信息已更新");
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    // ── Paragraph CRUD: insert / update / delete / reorder actions & dialogues ──

    @PostMapping("/{id}/scenes/{sceneId}/actions")
    @Operation(summary = "向场景中插入一个动作段落")
    public ResponseEntity<Object> addAction(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @RequestBody Map<String, Object> body) {
        String description = (String) body.get("description");
        if (description == null || description.isBlank()) {
            return badRequest("description 字段不能为空");
        }

        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    String actionType = body.get("actionType") instanceof String s
                            ? s : "ACTION";
                    int sequence = body.get("sequence") instanceof Number n
                            ? n.intValue() : 999;
                    Long characterId = body.get("characterId") instanceof Number n
                            ? n.longValue() : null;
                    Integer durationMs = body.get("durationMs") instanceof Number n
                            ? n.intValue() : null;

                    Action action = Action.builder()
                            .id(System.currentTimeMillis())
                            .sceneId(sceneId)
                            .characterId(characterId)
                            .sequence(sequence)
                            .actionType(actionType)
                            .description(description.trim())
                            .durationMs(durationMs)
                            .build();

                    try {
                        scriptService.addAction(id, sceneId, action);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("id", action.getId());
                    resp.put("message", "动作段落已插入");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    @PutMapping("/{id}/scenes/{sceneId}/actions/{actionId}")
    @Operation(summary = "更新场景中的动作段落")
    public ResponseEntity<Object> updateAction(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @PathVariable Long actionId,
            @RequestBody Map<String, Object> body) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    try {
                        scriptService.updateAction(id, sceneId, actionId, body);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("message", "动作段落已更新");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    @DeleteMapping("/{id}/scenes/{sceneId}/actions/{actionId}")
    @Operation(summary = "删除场景中的动作段落")
    public ResponseEntity<Object> deleteAction(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @PathVariable Long actionId) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    try {
                        scriptService.deleteAction(id, sceneId, actionId);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("message", "动作段落已删除");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    @PostMapping("/{id}/scenes/{sceneId}/dialogues")
    @Operation(summary = "向场景中插入一个对白段落")
    public ResponseEntity<Object> addDialogue(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        String speaker = (String) body.get("speaker");
        if (content == null || content.isBlank()) {
            return badRequest("content 字段不能为空");
        }
        if (speaker == null || speaker.isBlank()) {
            return badRequest("speaker 字段不能为空");
        }

        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    int sequence = body.get("sequence") instanceof Number n
                            ? n.intValue() : 999;
                    Long characterId = body.get("characterId") instanceof Number n
                            ? n.longValue() : 0L;
                    String emotion = body.get("emotion") instanceof String s
                            ? s : "NEUTRAL";
                    String parenthetical = body.get("parenthetical") instanceof String s
                            ? s : null;

                    Dialogue dialogue = Dialogue.builder()
                            .id(System.currentTimeMillis())
                            .sceneId(sceneId)
                            .characterId(characterId)
                            .sequence(sequence)
                            .speaker(speaker.trim())
                            .emotion(Emotion.fromLabel(emotion))
                            .content(content.trim())
                            .parenthetical(parenthetical)
                            .build();

                    try {
                        scriptService.addDialogue(id, sceneId, dialogue);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("id", dialogue.getId());
                    resp.put("message", "对白段落已插入");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    @PutMapping("/{id}/scenes/{sceneId}/dialogues/{dialogueId}")
    @Operation(summary = "更新场景中的对白段落")
    public ResponseEntity<Object> updateDialogueParagraph(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @PathVariable Long dialogueId,
            @RequestBody Map<String, Object> body) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    try {
                        scriptService.updateDialogue(id, sceneId, dialogueId, body);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("message", "对白段落已更新");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    @DeleteMapping("/{id}/scenes/{sceneId}/dialogues/{dialogueId}")
    @Operation(summary = "删除场景中的对白段落")
    public ResponseEntity<Object> deleteDialogue(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @PathVariable Long dialogueId) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    try {
                        scriptService.deleteDialogue(id, sceneId, dialogueId);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("message", "对白段落已删除");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    @PutMapping("/{id}/scenes/{sceneId}/reorder")
    @Operation(summary = "重新排列场景中所有段落的顺序")
    public ResponseEntity<Object> reorderContent(
            @PathVariable Long id,
            @PathVariable Long sceneId,
            @RequestBody Map<String, Object> body) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
                    if (items == null || items.isEmpty()) {
                        return badRequest("items 字段不能为空");
                    }
                    try {
                        scriptService.reorderSceneContent(id, sceneId, items);
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("code", 404);
                        err.put("message", e.getMessage());
                        return ResponseEntity.status(404).body(err);
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("message", "段落顺序已更新");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> scriptNotFound(id));
    }

    // ── Helpers ──

    private ResponseEntity<Object> badRequest(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", 400);
        err.put("message", message);
        return ResponseEntity.badRequest().body(err);
    }

    private ResponseEntity<Object> scriptNotFound(Long id) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", 404);
        err.put("message", "剧本不存在: id=" + id);
        return ResponseEntity.status(404).body(err);
    }

    // ── Plot Insertions: user-inserted narrative text between scenes ──

    @GetMapping("/{id}/plot-insertions")
    @Operation(summary = "获取剧本中所有用户插入的情节文本")
    public ResponseEntity<Object> getPlotInsertions(@PathVariable Long id) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    List<PlotInsertion> insertions = script.getPlotInsertions();
                    return ResponseEntity.ok(insertions != null ? insertions : List.of());
                })
                .orElseGet(() -> {
                    Map<String, Object> notFound = new LinkedHashMap<>();
                    notFound.put("code", 404);
                    notFound.put("message", "剧本不存在: id=" + id);
                    return ResponseEntity.status(404).body(notFound);
                });
    }

    @PostMapping("/{id}/plot-insertions")
    @Operation(summary = "手动插入一段情节描述文本到剧本中")
    public ResponseEntity<Object> addPlotInsertion(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        if (text == null || text.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("code", 400);
            err.put("message", "text 字段不能为空");
            return ResponseEntity.badRequest().body(err);
        }

        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    int position = body.containsKey("position")
                            ? ((Number) body.get("position")).intValue()
                            : script.getScenes().size(); // default: after last scene

                    PlotInsertion insertion = PlotInsertion.builder()
                            .id(System.currentTimeMillis()) // simple unique ID
                            .scriptId(id)
                            .text(text.trim())
                            .position(Math.max(0, position))
                            .insertedBy("user")
                            .createdAt(java.time.LocalDateTime.now())
                            .updatedAt(java.time.LocalDateTime.now())
                            .build();

                    script.getPlotInsertions().add(insertion);
                    scriptService.save(script);
                    log.info("Plot insertion added: scriptId={}, position={}, textLen={}",
                            id, insertion.getPosition(), insertion.getText().length());

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("id", insertion.getId());
                    resp.put("position", insertion.getPosition());
                    resp.put("message", "情节插入已添加");
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> {
                    Map<String, Object> notFound = new LinkedHashMap<>();
                    notFound.put("code", 404);
                    notFound.put("message", "剧本不存在: id=" + id);
                    return ResponseEntity.status(404).body(notFound);
                });
    }

    @DeleteMapping("/{id}/plot-insertions/{insertionId}")
    @Operation(summary = "删除指定的情节插入")
    public ResponseEntity<Object> removePlotInsertion(
            @PathVariable Long id,
            @PathVariable Long insertionId) {
        return scriptService.findById(id)
                .<ResponseEntity<Object>>map(script -> {
                    boolean removed = script.getPlotInsertions()
                            .removeIf(pi -> pi.getId().equals(insertionId));
                    if (removed) {
                        scriptService.save(script);
                        log.info("Plot insertion removed: scriptId={}, insertionId={}", id, insertionId);
                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("message", "情节插入已删除");
                        return ResponseEntity.ok(resp);
                    }
                    Map<String, Object> notFound = new LinkedHashMap<>();
                    notFound.put("code", 404);
                    notFound.put("message", "情节插入不存在: id=" + insertionId);
                    return ResponseEntity.status(404).body(notFound);
                })
                .orElseGet(() -> {
                    Map<String, Object> notFound = new LinkedHashMap<>();
                    notFound.put("code", 404);
                    notFound.put("message", "剧本不存在: id=" + id);
                    return ResponseEntity.status(404).body(notFound);
                });
    }
}
