package com.novel2script.api.controller;

import com.novel2script.application.service.NovelService;
import com.novel2script.application.service.ScriptService;
import com.novel2script.common.enums.WorkflowStep;
import com.novel2script.domain.dto.GenerationProgress;
import com.novel2script.domain.dto.ScriptGenerateRequest;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        emitter.onCompletion(scheduler::shutdownNow);
        emitter.onTimeout(scheduler::shutdownNow);
        emitter.onError(e -> {
            log.warn("SSE error for script id={}: {}", id, e.getMessage());
            scheduler.shutdownNow();
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                double progress = scriptService.getProgress(id);
                Optional<Script> scriptOpt = scriptService.findById(id);

                GenerationProgress gp = new GenerationProgress(
                        String.valueOf(id),
                        WorkflowStep.CHAPTER_PARSE, // placeholder — would come from workflow state
                        progress,
                        scriptOpt.map(s -> s.getStatus() != null ? s.getStatus().name() : "UNKNOWN")
                                .orElse("UNKNOWN"),
                        Instant.now(),
                        progress >= 100.0 ? Instant.now() : Instant.now().plusSeconds(300),
                        scriptOpt.map(Script::getTitle).orElse("Generating...")
                );

                emitter.send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name("progress")
                        .data(gp));

                if (progress >= 100.0) {
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(Map.of("scriptId", id, "status", "COMPLETED")));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("Failed to send SSE for script id={}: {}", id, e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage())));
                } catch (Exception ignored) {
                    // ignore send errors during error handling
                }
                emitter.completeWithError(e);
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
    public ResponseEntity<Map<String, Object>> updateCharacter(
            @PathVariable Long id,
            @PathVariable Long characterId,
            @RequestBody Map<String, Object> updates) {

        log.info("Update character: scriptId={}, characterId={}", id, characterId);

        // Placeholder — in production this would delegate to ScriptService
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scriptId", id);
        response.put("characterId", characterId);
        response.put("updated", true);
        response.put("message", "角色更新已接收（功能开发中）");
        return ResponseEntity.ok(response);
    }
}
