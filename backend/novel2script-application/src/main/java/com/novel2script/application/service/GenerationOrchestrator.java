package com.novel2script.application.service;

import com.novel2script.application.service.agent.*;
import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.application.service.agent.model.CompositionInput;
import com.novel2script.common.enums.*;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Novel;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import com.novel2script.domain.model.Character;  // explicit — not java.lang.Character
import com.novel2script.infrastructure.config.AiModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Orchestrates the full Novel → Script generation pipeline.
 *
 * <p>Each step calls the corresponding AI agent. If an agent fails
 * (e.g. no API key, network error, model unavailable), the pipeline either
 * falls back to deterministic extraction from the source text (dialogue regex)
 * or records an explicit failure — it NEVER injects fabricated content.
 */
@Slf4j
@Service
public class GenerationOrchestrator {

    private final ScriptService scriptService;
    private final NovelService novelService;
    private final AiModelRouter modelRouter;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<Long, Boolean> runningGenerations = new ConcurrentHashMap<>();

    /** Config: skip the single-pass generation attempt and go directly to multi-step. */
    private final boolean singlePassEnabled;

    /** Config: use staged v2.0 mode (outline → dialogue/action fill). */
    private final boolean singlePassStaged;

    // ── New single-pass agent (primary) ──
    private final ScriptGenerationAgent scriptGenerationAgent;

    // ── Legacy multi-step agents (fallback) ──
    private final CharacterAgent characterAgent;
    private final CharacterResolverAgent characterResolverAgent;
    private final PlotExtractionAgent plotExtractionAgent;
    private final SceneAgent sceneAgent;
    private final DialogueAgent dialogueAgent;
    private final ActionAgent actionAgent;
    private final ScriptComposer scriptComposer;
    /** Global counter for dialogue IDs assigned in fallback extraction */
    private final java.util.concurrent.atomic.AtomicLong dialogueIdSeq = new java.util.concurrent.atomic.AtomicLong(50000);

    public GenerationOrchestrator(ScriptService scriptService,
                                  NovelService novelService,
                                  AiModelRouter modelRouter,
                                  @org.springframework.beans.factory.annotation.Value("${script.generation.single-pass.enabled:false}") boolean singlePassEnabled,
                                  @org.springframework.beans.factory.annotation.Value("${script.generation.single-pass.staged:true}") boolean singlePassStaged,
                                  ScriptGenerationAgent scriptGenerationAgent,
                                  CharacterAgent characterAgent,
                                  CharacterResolverAgent characterResolverAgent,
                                  PlotExtractionAgent plotExtractionAgent,
                                  SceneAgent sceneAgent,
                                  DialogueAgent dialogueAgent,
                                  ActionAgent actionAgent,
                                  ScriptComposer scriptComposer) {
        this.scriptService = scriptService;
        this.novelService = novelService;
        this.modelRouter = modelRouter;
        this.singlePassEnabled = singlePassEnabled;
        this.singlePassStaged = singlePassStaged;
        this.scriptGenerationAgent = scriptGenerationAgent;
        this.characterAgent = characterAgent;
        this.characterResolverAgent = characterResolverAgent;
        this.plotExtractionAgent = plotExtractionAgent;
        this.sceneAgent = sceneAgent;
        this.dialogueAgent = dialogueAgent;
        this.actionAgent = actionAgent;
        this.scriptComposer = scriptComposer;
        log.info("GenerationOrchestrator: singlePassEnabled={}, singlePassStaged={}",
                singlePassEnabled, singlePassStaged);
    }

    public void launchGeneration(Script script) {
        Long scriptId = script.getId();
        runningGenerations.put(scriptId, true);
        executor.submit(() -> {
            try {
                runPipeline(script);
            } catch (Exception e) {
                log.error("Pipeline failed for script id={}", scriptId, e);
                scriptService.markFailed(scriptId);
            } finally {
                runningGenerations.remove(scriptId);
            }
        });
    }

    // ═══════════════════════════════════════════════════════
    //  Main Pipeline — single-pass primary, multi-step fallback
    // ═══════════════════════════════════════════════════════

    private void runPipeline(Script script) {
        Long scriptId = script.getId();
        Long novelId = script.getNovelId();

        Optional<Novel> novelOpt = novelService.findById(novelId);
        if (novelOpt.isEmpty()) {
            log.warn("Novel not found for script id={}", scriptId);
            scriptService.markFailed(scriptId);
            return;
        }
        Novel novel = novelOpt.get();
        List<Chapter> chapters = novel.getChapters();
        if (chapters == null || chapters.isEmpty()) {
            log.warn("Novel has no chapters for script id={}", scriptId);
            scriptService.markFailed(scriptId);
            return;
        }

        int totalChars = chapters.stream().mapToInt(c -> c.getContent() != null ? c.getContent().length() : 0).sum();
        log.info("Pipeline starting: scriptId={}, novel='{}', chapters={}, totalChars={}",
                scriptId, novel.getTitle(), chapters.size(), totalChars);

        // Print AI model info
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("🔧 AI 模型配置:");
        log.info("  可用 Providers: {}", modelRouter.availableModels().keySet());
        printModelInfo(TaskType.SCRIPT_COMPOSE, "剧本生成（单次转换）");
        log.info("  模式: 单次 AI 调用 → 完整剧本 JSON");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // ── SINGLE-PASS AI GENERATION (configurable) ──────
        if (singlePassEnabled) {
            updateStep(script, WorkflowStep.SCRIPT_COMPOSE);
            scriptService.updateProgress(scriptId, 10.0, WorkflowStep.SCRIPT_COMPOSE);

            try {
                if (singlePassStaged) {
                    // ── v2.0 Staged mode: outline first, then dialogue/action fill ──
                    log.info("Calling ScriptGenerationAgent.generateOutline() — v2.0 staged: outline → dialogue fill");
                    Script outline = scriptGenerationAgent.generateOutline(chapters, novel.getTitle(), scriptId, true);

                    if (outline != null && !outline.getScenes().isEmpty()
                            && !outline.getCharacters().isEmpty()) {

                        // ── Phase 1 complete: immediately save characters + scene outlines ──
                        scriptService.updateProgress(scriptId, 50.0, WorkflowStep.SCRIPT_COMPOSE);
                        script.setTitle(outline.getTitle());
                        script.setCharacters(outline.getCharacters());
                        script.setCharacterCount(outline.getCharacterCount());
                        script.setVersion(1);

                        // Ensure scenes have empty dialogue/action lists
                        List<Scene> outlineScenes = outline.getScenes();
                        for (Scene s : outlineScenes) {
                            if (s.getDialogues() == null) s.setDialogues(new ArrayList<>());
                            if (s.getActions() == null) s.setActions(new ArrayList<>());
                        }
                        script.setScenes(outlineScenes);
                        script.setSceneCount(outlineScenes.size());
                        script.setPlotEvents(new ArrayList<>());

                        // ── IMMEDIATE SAVE: preserve outline even if dialogue fill fails ──
                        scriptService.updateCharacters(scriptId, outline.getCharacters());
                        scriptService.updateScenes(scriptId, outlineScenes);
                        scriptService.updateProgress(scriptId, 55.0, WorkflowStep.SCRIPT_COMPOSE);

                        log.info("✅ Phase 1 (outline) complete: {} characters, {} scene outlines — SAVED",
                                outline.getCharacterCount(), outline.getSceneCount());

                        // ── Phase 2: Fill dialogues & actions per scene (parallel, with fallback) ──
                        scriptService.updateProgress(scriptId, 60.0, WorkflowStep.DIALOGUE_GENERATE);
                        List<Scene> filledScenes = generateDialoguesWithFallback(
                                scriptId, outlineScenes, outline.getCharacters());
                        scriptService.updateScenes(scriptId, filledScenes);
                        scriptService.updateProgress(scriptId, 80.0, WorkflowStep.DIALOGUE_GENERATE);

                        scriptService.updateProgress(scriptId, 85.0, WorkflowStep.ACTION_GENERATE);
                        filledScenes = generateActionsWithFallback(scriptId, filledScenes,
                                outline.getCharacters(),
                                filledScenes.stream().flatMap(s -> s.getDialogues().stream()).toList());
                        scriptService.updateScenes(scriptId, filledScenes);
                        scriptService.updateProgress(scriptId, 95.0, WorkflowStep.ACTION_GENERATE);

                        // ── Complete ──
                        script.setScenes(filledScenes);
                        script.setDialogueCount(filledScenes.stream().mapToInt(s -> s.getDialogues().size()).sum());
                        scriptService.updateScenes(scriptId, filledScenes);
                        scriptService.updateProgress(scriptId, 100.0, WorkflowStep.SCRIPT_COMPOSE);
                        scriptService.completeScript(scriptId);

                        log.info("✅ Staged generation complete: {} characters, {} scenes, {} dialogues",
                                outline.getCharacterCount(), outline.getSceneCount(),
                                script.getDialogueCount());
                        return;
                    }

                    log.warn("Phase 1 (outline) generation returned insufficient data, falling back to multi-step...");
                } else {
                    // ── v1.0 legacy: full JSON generation in one call ──
                    log.info("Calling ScriptGenerationAgent.generate() — v1.0 single-pass: full JSON");
                    Script generated = scriptGenerationAgent.generate(chapters, novel.getTitle(), scriptId);

                    if (generated != null && !generated.getScenes().isEmpty()
                            && !generated.getCharacters().isEmpty()) {
                        scriptService.updateProgress(scriptId, 80.0, WorkflowStep.SCRIPT_COMPOSE);
                        script.setTitle(generated.getTitle());
                        script.setCharacters(generated.getCharacters());
                        script.setCharacterCount(generated.getCharacterCount());
                        script.setScenes(generated.getScenes());
                        script.setSceneCount(generated.getSceneCount());
                        script.setDialogueCount(generated.getDialogueCount());
                        script.setPlotEvents(new ArrayList<>());
                        script.setVersion(1);

                        scriptService.updateCharacters(scriptId, generated.getCharacters());
                        scriptService.updateScenes(scriptId, generated.getScenes());
                        scriptService.updateProgress(scriptId, 100.0, WorkflowStep.SCRIPT_COMPOSE);
                        scriptService.completeScript(scriptId);

                        log.info("✅ Single-pass generation complete: {} characters, {} scenes, {} dialogues",
                                generated.getCharacterCount(), generated.getSceneCount(),
                                generated.getDialogueCount());
                        return;
                    }

                    log.warn("Single-pass generation returned insufficient data, falling back to multi-step...");
                }
            } catch (Exception e) {
                log.warn("Single-pass generation failed: {} — falling back to multi-step...", e.getMessage());

                // ── PARTIAL SAVE: try to save whatever was generated before the error ──
                try {
                    Script partialScript = scriptService.findById(scriptId).orElse(null);
                    if (partialScript != null && partialScript.getCharacters() != null
                            && !partialScript.getCharacters().isEmpty()
                            && partialScript.getScenes() != null
                            && !partialScript.getScenes().isEmpty()) {
                        log.info("Partial result preserved: {} characters, {} scene outlines — will fill with fallback",
                                partialScript.getCharacters().size(), partialScript.getScenes().size());
                        // Fall through to runMultiStepPipeline which will fill dialogues/actions
                        // with AI or source-extracted content only — never fabricated data
                    }
                } catch (Exception ex) {
                    log.debug("Could not check partial save state: {}", ex.getMessage());
                }
            }
        } else {
            log.info("⏭️  Single-pass generation disabled (script.generation.single-pass.enabled=false), "
                    + "using multi-step pipeline directly");
        }

        // ── Multi-step pipeline ───────────────────────────
        runMultiStepPipeline(script, novel, chapters);
    }

    /**
     * Legacy multi-step pipeline as fallback when single-pass fails.
     * <p>
     * If single-pass v2.0 (staged) already saved characters and scene outlines
     * (partial result), this method skips directly to dialogue/action generation.
     */
    private void runMultiStepPipeline(Script script, Novel novel, List<Chapter> chapters) {
        Long scriptId = script.getId();
        Long novelId = script.getNovelId();

        @SuppressWarnings("unchecked")
        List<String> focusCharacters = (List<String>) script.getWorkflowState()
                .getOrDefault("focusCharacters", List.of());

        // ── PARTIAL RECOVERY: check if characters + scenes already saved from failed single-pass ──
        List<Character> characters = script.getCharacters();
        List<Scene> scenes = script.getScenes();
        boolean hasPartialResult = characters != null && !characters.isEmpty()
                && scenes != null && !scenes.isEmpty();

        if (hasPartialResult) {
            log.info("♻️  Partial recovery: {} characters and {} scene outlines already saved from single-pass attempt",
                    characters.size(), scenes.size());
            log.info("   Skipping character extraction and scene segmentation → generating dialogues/actions directly");

            // Update progress to reflect we're starting from dialogue generation
            scriptService.updateProgress(scriptId, 60.0, WorkflowStep.DIALOGUE_GENERATE);
        } else {
            printModelInfo(TaskType.CHARACTER_EXTRACTION, "角色提取（回退模式）");
            printModelInfo(TaskType.SCENE_SEGMENT, "场景切分（回退模式）");

            // Step 1: Character extraction
            updateStep(script, WorkflowStep.CHARACTER_EXTRACT);
            scriptService.updateProgress(scriptId, 15.0, WorkflowStep.CHARACTER_EXTRACT);
            List<CharacterExtractionResult> extractionResults = null;
            String charError = null;
            try {
                extractionResults = characterAgent.extract(chapters, focusCharacters);
            } catch (Exception e) {
                charError = e.getMessage();
            }
            if (extractionResults == null || extractionResults.isEmpty()) {
                String msg = charError != null ? "AI 角色提取失败: " + charError : "AI 无法识别角色，请检查 API Key";
                log.error("❌ Pipeline FAILED: {}", msg);
                scriptService.markFailed(scriptId);
                script.getWorkflowState().put("error", msg);
                return;
            }
            scriptService.updateProgress(scriptId, 25.0, WorkflowStep.CHARACTER_EXTRACT);

            // Resolve characters
            updateStep(script, WorkflowStep.CHARACTER_RESOLVE);
            try {
                characters = characterResolverAgent.resolve(extractionResults);
            } catch (Exception e) {
                characters = extractionResults.stream().map(CharacterExtractionResult::toDomainCharacter).toList();
            }
            AtomicInteger charIdSeq = new AtomicInteger((int)(scriptId * 1000));
            for (Character c : characters) {
                if (c.getId() == null) c.setId((long) charIdSeq.getAndIncrement());
                c.setScriptId(scriptId);
            }
            scriptService.updateCharacters(scriptId, characters);
            scriptService.updateProgress(scriptId, 35.0, WorkflowStep.CHARACTER_RESOLVE);

            // Scene segmentation
            updateStep(script, WorkflowStep.SCENE_SEGMENT);
            scriptService.updateProgress(scriptId, 45.0, WorkflowStep.SCENE_SEGMENT);
            String sceneError = null;
            try {
                scenes = sceneAgent.segment(chapters, new ArrayList<>(), characters);
            } catch (Exception e) {
                sceneError = e.getMessage();
                scenes = null;
            }
            if (scenes == null || scenes.isEmpty()) {
                String msg = sceneError != null ? "AI 场景切分失败: " + sceneError : "AI 无法切分场景，请检查 API Key";
                log.error("❌ Pipeline FAILED: {}", msg);
                scriptService.markFailed(scriptId);
                script.getWorkflowState().put("error", msg);
                return;
            }
            AtomicInteger sceneIdSeq = new AtomicInteger((int)(scriptId * 1000 + 100));
            for (Scene s : scenes) {
                if (s.getId() == null) s.setId((long) sceneIdSeq.getAndIncrement());
                s.setScriptId(scriptId);
                if (s.getDialogues() == null) s.setDialogues(new ArrayList<>());
                if (s.getActions() == null) s.setActions(new ArrayList<>());
            }
            scriptService.updateScenes(scriptId, scenes);
            scriptService.updateProgress(scriptId, 60.0, WorkflowStep.SCENE_SEGMENT);
        }

        // Dialogue generation — Tier 1: AI, Tier 2: regex extraction from source,
        // Tier 3: explicit failure (no fabricated content — scenes stay empty)
        updateStep(script, WorkflowStep.DIALOGUE_GENERATE);
        scriptService.updateProgress(scriptId, 65.0, WorkflowStep.DIALOGUE_GENERATE);
        scenes = generateDialoguesWithFallback(scriptId, scenes, characters);
        scriptService.updateScenes(scriptId, scenes);
        scriptService.updateProgress(scriptId, 80.0, WorkflowStep.DIALOGUE_GENERATE);

        // Action generation — AI only; on failure the scene is marked as pending
        // (no fabricated content — scenes stay empty)
        updateStep(script, WorkflowStep.ACTION_GENERATE);
        scriptService.updateProgress(scriptId, 85.0, WorkflowStep.ACTION_GENERATE);
        scenes = generateActionsWithFallback(scriptId, scenes, characters, scenes.stream()
                .flatMap(s -> s.getDialogues().stream()).toList());
        scriptService.updateScenes(scriptId, scenes);
        scriptService.updateProgress(scriptId, 95.0, WorkflowStep.ACTION_GENERATE);

        // Complete
        String title = novel.getTitle();
        script.setTitle(title);
        script.setCharacters(characters);
        script.setScenes(scenes);
        script.setCharacterCount(characters.size());
        script.setSceneCount(scenes.size());
        script.setDialogueCount(scenes.stream().mapToInt(s -> s.getDialogues().size()).sum());
        scriptService.setTitle(scriptId, title);
        scriptService.updateScenes(scriptId, scenes);
        scriptService.updateCharacters(scriptId, characters);

        scriptService.updateProgress(scriptId, 100.0, WorkflowStep.SCRIPT_COMPOSE);
        scriptService.completeScript(scriptId);
        log.info("Multi-step fallback completed: {} characters, {} scenes",
                characters.size(), scenes.size());
    }

    // ── Helpers ────────────────────────────────────────────

    private void updateStep(Script script, WorkflowStep step) {
        script.getWorkflowState().put("currentStep", step.name());
    }

    /** Print which model (provider + model name) will be used for a task */
    private void printModelInfo(TaskType taskType, String taskName) {
        try {
            var model = modelRouter.route(taskType);
            String modelStr = model.toString();
            // Extract model name from OpenAiChatModel toString (format varies)
            log.info("  {}: {} → {}", taskName, taskType.getDefaultProvider(), modelStr);
        } catch (Exception e) {
            log.warn("  {}: {} → (unavailable — will fallback)", taskName, taskType.getDefaultProvider());
        }
    }

    /**
     * Resolve which characters are present in a scene for dialogue/action generation.
     * Uses scene.getCharacterIds() if available; otherwise returns ALL known characters
     * as candidates for AI generation (does NOT persist — only affects runtime).
     *
     * <p>IMPORTANT: This method is a runtime helper for AI agents, NOT a persistence
     * mechanism. Returning all characters when characterIds is empty allows the AI
     * to decide who speaks in each scene, preventing the "zero dialogue" cascade failure.
     */
    private List<Character> resolveCharactersForScene(Scene scene, List<Character> allCharacters) {
        if (scene.getCharacterIds() != null && !scene.getCharacterIds().isEmpty()) {
            List<Character> result = new ArrayList<>();
            for (Long cid : scene.getCharacterIds()) {
                allCharacters.stream()
                        .filter(c -> cid.equals(c.getId()))
                        .findFirst()
                        .ifPresent(result::add);
            }
            if (!result.isEmpty()) return result;
        }
        // Fallback: return ALL characters so AI can still generate dialogue.
        // Required because SceneAgent.resolveCharacterIds() may fail to match
        // character names in chapter text, leaving characterIds empty.
        log.debug("No character IDs for scene '{}' — using all {} characters as candidates",
                scene.getTitle(), allCharacters.size());
        return new ArrayList<>(allCharacters);
    }

    private List<PlotEvent> resolveEventsForScene(Scene scene, List<PlotEvent> allEvents) {
        if (scene.getChapterIds() != null && !scene.getChapterIds().isEmpty()) {
            return allEvents.stream()
                    .filter(e -> e.getChapterIds() != null && e.getChapterIds().stream()
                            .anyMatch(chId -> scene.getChapterIds().contains(chId)))
                    .toList();
        }
        return allEvents;
    }

    // ═══════════ Dialogue & Action generation with fallback ═══════════

    /**
     * Generate dialogues for all scenes with three-tier fallback.
     * Scenes are processed in parallel to reduce wall-clock time
     * (was ~90s serial, now ~20s with 5-thread pool).
     */
    private List<Scene> generateDialoguesWithFallback(Long scriptId, List<Scene> scenes, List<Character> characters) {
        if (characters == null || characters.isEmpty()) {
            // No fabrication: without characters we cannot attribute any dialogue.
            // Mark every scene as pending instead of injecting placeholder lines.
            log.warn("No characters available for dialogue generation — {} scenes marked as pending, no lines fabricated",
                    scenes.size());
            for (Scene scene : scenes) {
                scene.setDialogues(new ArrayList<>());
                scene.setDialogueStatus(GenerationStatus.FAILED);
            }
            recordDialogueFailure(scriptId, scenes, scenes.size());
            return scenes;
        }

        // Invalidate dialogue agent's character profile cache for fresh batch
        dialogueAgent.invalidateProfileCache();

        AtomicInteger aiSuccessCount = new AtomicInteger(0);
        AtomicInteger speechFallbackCount = new AtomicInteger(0);
        AtomicInteger failedScenes = new AtomicInteger(0);
        AtomicInteger totalDialogues = new AtomicInteger(0);

        int parallelism = Math.min(scenes.size(), 5);
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Track previous scene's dialogues for narrative continuity
        final List<Dialogue>[] previousDialogues = new List[1];
        previousDialogues[0] = null;

        for (int si = 0; si < scenes.size(); si++) {
            final Scene scene = scenes.get(si);
            final Scene prevScene = si > 0 ? scenes.get(si - 1) : null;
            // Capture previous dialogues for continuity (snapshot before parallel mutation)
            final List<Dialogue> prevDias = previousDialogues[0];

            futures.add(CompletableFuture.runAsync(() -> {
                List<Character> presentChars = resolveCharactersForScene(scene, characters);
                List<Dialogue> dialogues;

                // Tier 1: AI generation (with previous scene's dialogues for continuity)
                try {
                    dialogues = dialogueAgent.generate(scene, presentChars, List.of(),
                            prevScene, null, prevDias);
                    if (dialogues != null && !dialogues.isEmpty()) {
                        markDialogueSource(dialogues, ContentSource.AI);
                        synchronized (scene) {
                            scene.setDialogues(dialogues);
                            scene.setDialogueStatus(GenerationStatus.COMPLETED);
                            previousDialogues[0] = dialogues; // update for next scene
                        }
                        aiSuccessCount.incrementAndGet();
                        totalDialogues.addAndGet(dialogues.size());
                        return;
                    }
                } catch (Exception e) {
                    log.debug("AI dialogue failed for '{}': {}", scene.getTitle(), e.getMessage());
                }

                // Tier 2: regex extraction — deterministic, taken straight from the
                // source text, so its provenance is REGEX rather than AI
                dialogues = extractDialoguesFromContext(scene, presentChars);
                if (!dialogues.isEmpty()) {
                    markDialogueSource(dialogues, ContentSource.REGEX);
                    synchronized (scene) {
                        scene.setDialogues(dialogues);
                        scene.setDialogueStatus(GenerationStatus.COMPLETED);
                        previousDialogues[0] = dialogues;
                    }
                    speechFallbackCount.incrementAndGet();
                    totalDialogues.addAndGet(dialogues.size());
                    return;
                }

                // Tier 3: no fabrication — explicitly fail, write nothing
                // (历史遗留的硬编码台词兜底已移除；现改为显式失败并标记待补全)
                synchronized (scene) {
                    scene.setDialogues(new ArrayList<>());
                    scene.setDialogueStatus(GenerationStatus.FAILED);
                }
                failedScenes.incrementAndGet();
                log.warn("对白生成失败，场景 '{}' 标记为待补全（不编造台词）", scene.getTitle());
            }, pool));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Parallel dialogue generation timed out: {}", e.getMessage());
        } finally {
            pool.shutdownNow();
        }

        int failed = failedScenes.get();
        if (failed > 0) {
            log.warn("⚠️  对白生成：{}/{} 个场景无对白（AI 与原文抽取均失败）——不编造台词，待人工补全",
                    failed, scenes.size());
            recordDialogueFailure(scriptId, scenes, failed);
        }

        // ── Quality metrics logging ──
        int sceneCount = scenes.size();
        int aiCount = aiSuccessCount.get();
        int speechCount = speechFallbackCount.get();
        double aiRatio = sceneCount > 0 ? (double) aiCount / sceneCount * 100.0 : 0;
        double avgDias = sceneCount > 0 ? (double) totalDialogues.get() / sceneCount : 0;

        log.info("📊 Dialogue quality: {}/{} scenes AI-generated ({:.0f}%), speech-extract={}, failed(no fabrication)={}, "
                + "totalDialogues={}, avgPerScene={:.1f} (parallelism={})",
                aiCount, sceneCount, aiRatio, speechCount, failed,
                totalDialogues.get(), avgDias, parallelism);

        return scenes;
    }

    /**
     * Stamp every generated line with its provenance so the DB can later tell
     * AI output apart from regex-extracted text and human edits.
     *
     * <p>Named per content type rather than overloaded: {@code List<Dialogue>}
     * and {@code List<Action>} erase to the same signature.
     */
    private static void markDialogueSource(List<Dialogue> dialogues, ContentSource source) {
        if (dialogues == null) return;
        for (Dialogue d : dialogues) {
            if (d != null) d.setSource(source);
        }
    }

    /** Stamp every generated action with its provenance. */
    private static void markActionSource(List<Action> actions, ContentSource source) {
        if (actions == null) return;
        for (Action a : actions) {
            if (a != null) a.setSource(source);
        }
    }

    /**
     * Persist dialogue-generation failure info into the script's workflowState
     * so the frontend can surface "待补全" scenes instead of silently showing
     * fabricated dialogue as success.
     */
    private void recordDialogueFailure(Long scriptId, List<Scene> scenes, int failedCount) {
        if (scriptId == null) return;
        try {
            List<Long> failedSceneIds = scenes.stream()
                    .filter(s -> s.getDialogues() == null || s.getDialogues().isEmpty())
                    .map(Scene::getId)
                    .toList();
            Map<String, Object> warnings = new LinkedHashMap<>();
            warnings.put("failedDialogueScenes", failedCount);
            warnings.put("failedDialogueSceneIds", failedSceneIds);
            scriptService.recordGenerationWarnings(scriptId, warnings);
        } catch (Exception e) {
            log.debug("Could not record dialogue failure warning: {}", e.getMessage());
        }
    }

    /**
     * Try to extract simple dialogues from scene context when AI returns empty.
     * Looks for speech indicators in the scene summary or chapter content.
     */
    private List<Dialogue> extractDialoguesFromContext(Scene scene, List<Character> presentCharacters) {
        List<Dialogue> dialogues = new ArrayList<>();

        String summary = scene.getSummary();
        if (summary == null || summary.isBlank()) return dialogues;

        // Patterns for direct speech in Chinese text
        // Group 1: speaker name (1-6 chars before speech verb)
        // Group 2: speech content (2-60 chars after speech marker)
        // e.g. XX说："..."  or  "..."XX道  or 「...」XX说
        java.util.regex.Pattern speechPattern = java.util.regex.Pattern.compile(
                "([^：:\"'\"'「『\\s]{1,6})" +             // group 1: speaker name
                "(?:冷冷|淡淡|低声|大声|轻声|小声|怒|笑|哭|吼|喊)?" +  // optional modifier (non-capturing)
                "(?:说道|说道：|说：|说|道：|道|喊道|问道|答道|回道|答|问)" + // speech verb (non-capturing)
                "[：:\"'\"『「]?" +                          // optional opening quote/punctuation
                "(.{2,60})" +                               // group 2: speech content
                "[\"'\"」』]?");                             // optional closing quote
        java.util.regex.Matcher m = speechPattern.matcher(summary);

        // Noise words that are clearly not character names
        java.util.Set<String> noiseSpeakers = java.util.Set.of(
            "他", "她", "它", "我", "你", "您", "俺", "咱", "谁",
            "一人", "两人", "三人", "众人", "大家", "有人", "某人",
            "来人", "旁人", "别人", "那人", "这人", "此人",
            "一个", "这个", "那个", "哪个"
        );

        int seq = 0;
        int groupCount = m.groupCount();
        while (m.find() && seq < 10) {
            String speakerName = m.group(1).trim();
            // Skip obvious noise — pronouns, quantifiers, single-char non-names
            if (speakerName.length() <= 1 || noiseSpeakers.contains(speakerName)) {
                continue;
            }
            // Safely get content: always use group 2 (the only content group)
            String content = (groupCount >= 2 && m.group(2) != null)
                    ? m.group(2).trim()
                    : "";

            // Try to match speaker to a present character
            Long characterId = null;
            String matchedName = speakerName;
            for (Character c : presentCharacters) {
                if (c.getCanonicalName() != null && c.getCanonicalName().contains(speakerName)) {
                    characterId = c.getId();
                    matchedName = c.getCanonicalName();
                    break;
                }
                if (speakerName.contains(c.getCanonicalName())) {
                    characterId = c.getId();
                    matchedName = c.getCanonicalName();
                    break;
                }
            }

            // Skip if speaker name doesn't match any known character
            // (don't force-assign noise like "他"/"众人" to a random character)
            if (characterId == null) {
                continue;
            }

            Dialogue d = Dialogue.builder()
                    .id(dialogueIdSeq.getAndIncrement())
                    .sceneId(scene.getId())
                    .characterId(characterId)
                    .speaker(matchedName)
                    .content(content)
                    .emotion(Emotion.CALM)
                    .sequence(seq + 1)
                    .createdAt(LocalDateTime.now())
                    .build();
            dialogues.add(d);
            seq++;
        }

        if (!dialogues.isEmpty()) {
            log.info("Extracted {} dialogues from scene '{}' context (speech pattern fallback)",
                    dialogues.size(), scene.getTitle());
        }
        return dialogues;
    }

    /**
     * Generate actions for all scenes with two-tier fallback, executed in parallel.
     * Was ~113s serial, now ~25s with 5-thread pool.
     */
    private List<Scene> generateActionsWithFallback(Long scriptId,
                                                     List<Scene> scenes,
                                                     List<Character> characters,
                                                     List<Dialogue> allDialogues) {
        if (characters == null || characters.isEmpty()) {
            // No fabrication: without characters we cannot attribute any action.
            // Mark every scene as pending instead of injecting placeholder actions.
            log.warn("No characters available for action generation — {} scenes marked as pending, no actions fabricated",
                    scenes.size());
            for (Scene scene : scenes) {
                scene.setActions(new ArrayList<>());
                scene.setActionStatus(GenerationStatus.FAILED);
            }
            recordActionFailure(scriptId, scenes, scenes.size());
            return scenes;
        }

        AtomicInteger aiSuccessCount = new AtomicInteger(0);
        AtomicInteger failedScenes = new AtomicInteger(0);

        int parallelism = Math.min(scenes.size(), 5);
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Scene scene : scenes) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<Character> presentChars = resolveCharactersForScene(scene, characters);
                List<Dialogue> sceneDialogues = allDialogues.stream()
                        .filter(d -> d.getSceneId() != null && d.getSceneId().equals(scene.getId()))
                        .toList();

                try {
                    List<Action> actions = actionAgent.generate(scene, sceneDialogues, presentChars);
                    if (actions != null && !actions.isEmpty()) {
                        markActionSource(actions, ContentSource.AI);
                        synchronized (scene) {
                            scene.setActions(actions);
                            scene.setActionStatus(GenerationStatus.COMPLETED);
                        }
                        aiSuccessCount.incrementAndGet();
                        return;
                    }
                } catch (Exception e) {
                    log.debug("AI action failed for '{}': {}", scene.getTitle(), e.getMessage());
                }

                // No fabrication — leave empty and mark for manual completion
                // (历史遗留的硬编码动作模板兜底已移除；现改为显式失败并标记待补全)
                synchronized (scene) {
                    scene.setActions(new ArrayList<>());
                    scene.setActionStatus(GenerationStatus.FAILED);
                }
                failedScenes.incrementAndGet();
                log.warn("动作生成失败，场景 '{}' 标记为待补全（不编造动作）", scene.getTitle());
            }, pool));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Parallel action generation timed out: {}", e.getMessage());
        } finally {
            pool.shutdownNow();
        }

        int failed = failedScenes.get();
        if (failed > 0) {
            log.warn("⚠️  动作生成：{}/{} 个场景无动作（AI 生成失败）——不编造动作，待人工补全",
                    failed, scenes.size());
            recordActionFailure(scriptId, scenes, failed);
        }

        log.info("Action generation: AI={}, failed(no fabrication)={} (parallelism={})",
                aiSuccessCount.get(), failed, parallelism);
        return scenes;
    }

    /**
     * Persist action-generation failure info into the script's workflowState
     * so the frontend can surface "待补全" scenes instead of silently showing
     * fabricated actions as success.
     */
    private void recordActionFailure(Long scriptId, List<Scene> scenes, int failedCount) {
        if (scriptId == null) return;
        try {
            List<Long> failedSceneIds = scenes.stream()
                    .filter(s -> s.getActions() == null || s.getActions().isEmpty())
                    .map(Scene::getId)
                    .toList();
            Map<String, Object> warnings = new LinkedHashMap<>();
            warnings.put("failedActionScenes", failedCount);
            warnings.put("failedActionSceneIds", failedSceneIds);
            scriptService.recordGenerationWarnings(scriptId, warnings);
        } catch (Exception e) {
            log.debug("Could not record action failure warning: {}", e.getMessage());
        }
    }

}
