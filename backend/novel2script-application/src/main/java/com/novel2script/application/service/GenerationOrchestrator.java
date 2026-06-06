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
 * (e.g. no API key, network error, model unavailable), the pipeline
 * falls back to mock data so the user can still see the full flow.
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
                                outlineScenes, outline.getCharacters());
                        scriptService.updateScenes(scriptId, filledScenes);
                        scriptService.updateProgress(scriptId, 80.0, WorkflowStep.DIALOGUE_GENERATE);

                        scriptService.updateProgress(scriptId, 85.0, WorkflowStep.ACTION_GENERATE);
                        filledScenes = generateActionsWithFallback(filledScenes, outline.getCharacters(),
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
                        // Fall through to runMultiStepPipeline which will fill dialogues/actions as mock
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

        // Dialogue generation — try AI first, fallback to speech-action extraction, then mock
        updateStep(script, WorkflowStep.DIALOGUE_GENERATE);
        scriptService.updateProgress(scriptId, 65.0, WorkflowStep.DIALOGUE_GENERATE);
        scenes = generateDialoguesWithFallback(scenes, characters);
        scriptService.updateScenes(scriptId, scenes);
        scriptService.updateProgress(scriptId, 80.0, WorkflowStep.DIALOGUE_GENERATE);

        // Action generation — try AI first, fallback to mock
        updateStep(script, WorkflowStep.ACTION_GENERATE);
        scriptService.updateProgress(scriptId, 85.0, WorkflowStep.ACTION_GENERATE);
        scenes = generateActionsWithFallback(scenes, characters, scenes.stream()
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

    /** Log whether AI or mock was used for a step result */
    private void logStepResult(String step, boolean aiSuccess, int resultCount) {
        if (aiSuccess) {
            log.info("✅ Step {}: AI produced {} results", step, resultCount);
        } else {
            log.warn("⚠️  Step {}: using MOCK data ({} items) — AI call failed or returned empty", step, resultCount);
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
    private List<Scene> generateDialoguesWithFallback(List<Scene> scenes, List<Character> characters) {
        if (characters == null || characters.isEmpty()) {
            log.warn("No characters available for dialogue generation");
            return buildDialoguesMock(scenes, characters);
        }

        AtomicInteger aiSuccessCount = new AtomicInteger(0);
        AtomicInteger speechFallbackCount = new AtomicInteger(0);
        AtomicInteger mockFallbackCount = new AtomicInteger(0);

        int parallelism = Math.min(scenes.size(), 5);
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Scene scene : scenes) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<Character> presentChars = resolveCharactersForScene(scene, characters);
                List<Dialogue> dialogues;

                // Tier 1: AI generation
                try {
                    dialogues = dialogueAgent.generate(scene, presentChars, List.of(), null, null);
                    if (dialogues != null && !dialogues.isEmpty()) {
                        synchronized (scene) { scene.setDialogues(dialogues); }
                        aiSuccessCount.incrementAndGet();
                        return;
                    }
                } catch (Exception e) {
                    log.debug("AI dialogue failed for '{}': {}", scene.getTitle(), e.getMessage());
                }

                // Tier 2: regex extraction
                dialogues = extractDialoguesFromContext(scene, presentChars);
                if (!dialogues.isEmpty()) {
                    synchronized (scene) { scene.setDialogues(dialogues); }
                    speechFallbackCount.incrementAndGet();
                    return;
                }

                // Tier 3: mock
                mockFallbackCount.incrementAndGet();
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

        if (mockFallbackCount.get() > 0) {
            // Only fill empty scenes — don't overwrite AI-generated dialogues
            for (Scene scene : scenes) {
                if (scene.getDialogues() == null || scene.getDialogues().isEmpty()) {
                    List<Character> presentChars = resolveCharactersForScene(scene, characters);
                    scene.setDialogues(buildMockDialoguesForScene(scene, presentChars));
                }
            }
        }

        log.info("Dialogue generation: AI={}, speech-extract={}, mock={} (parallelism={})",
                aiSuccessCount.get(), speechFallbackCount.get(), mockFallbackCount.get(), parallelism);
        return scenes;
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
    private List<Scene> generateActionsWithFallback(List<Scene> scenes,
                                                     List<Character> characters,
                                                     List<Dialogue> allDialogues) {
        if (characters == null || characters.isEmpty()) {
            return buildActionsMock(scenes, characters);
        }

        AtomicInteger aiSuccessCount = new AtomicInteger(0);
        AtomicInteger mockFallbackCount = new AtomicInteger(0);

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
                        synchronized (scene) { scene.setActions(actions); }
                        aiSuccessCount.incrementAndGet();
                        return;
                    }
                } catch (Exception e) {
                    log.debug("AI action failed for '{}': {}", scene.getTitle(), e.getMessage());
                }

                mockFallbackCount.incrementAndGet();
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

        if (mockFallbackCount.get() > 0) {
            // Only fill empty scenes — don't overwrite AI-generated actions
            for (Scene scene : scenes) {
                if (scene.getActions() == null || scene.getActions().isEmpty()) {
                    List<Character> presentChars = resolveCharactersForScene(scene, characters);
                    scene.setActions(buildMockActionsForScene(scene, presentChars));
                }
            }
        }

        log.info("Action generation: AI={}, mock={} (parallelism={})",
                aiSuccessCount.get(), mockFallbackCount.get(), parallelism);
        return scenes;
    }

    // ═══════════════ Mock fallback builders ═══════════════
    // Used when AI agents are unavailable (no API key, network error, etc.)

    private List<Character> buildCharactersMock(Long scriptId, Novel novel) {
        log.info("Using mock character data for scriptId={}", scriptId);
        List<Character> chars = new ArrayList<>();
        String title = novel.getTitle();
        int seed = Math.abs(title.hashCode());

        String[][] templates = {
            {"林川", "PROTAGONIST", "MALE", "25-30", "冷静睿智的主角，身怀秘密",
             "冷静,果断,善良", "25"},
            {"李雪", "SUPPORTING", "FEMALE", "23-28", "机智勇敢的女医生",
             "机智,勇敢,善良", "18"},
            {"王建国", "ANTAGONIST", "MALE", "45-55", "神秘组织的首领，城府极深",
             "冷酷,野心,狡猾", "20"},
            {"陈峰", "SUPPORTING", "MALE", "28-35", "退役特种兵，性格豪爽",
             "豪爽,忠诚,热血", "15"},
            {"苏雨晴", "SUPPORTING", "FEMALE", "20-25", "活泼开朗的大学生",
             "活泼,善良,聪明", "12"},
            {"老太太张", "MINOR", "FEMALE", "65-75", "慈祥的邻居老人",
             "慈祥,热心,传统", "5"},
            {"黑衣人首领", "ANTAGONIST", "MALE", "35-45", "神秘黑衣组织核心成员",
             "冷酷,沉默,高效", "8"},
            {"赵教授", "SUPPORTING", "MALE", "50-60", "考古学教授，博学多识",
             "博学,固执,正直", "10"},
        };

        String[][] relationships = {
            {"李雪", "青梅竹马"}, {"王建国", "宿敌"}, {"苏雨晴", "妹妹"},
            {"林川", "搭档"}, {"陈峰", "战友"}, {"林川", "救命恩人"},
            {"林川", "导师"}, {"王建国", "部下"},
        };

        AtomicInteger idSeq = new AtomicInteger((int)(scriptId * 1000));
        int charCount = Math.min(5 + (seed % 3), templates.length);

        for (int i = 0; i < charCount; i++) {
            String[] t = templates[(i + seed) % templates.length];
            Character c = new Character();
            c.setId((long) idSeq.getAndIncrement());
            c.setScriptId(scriptId);
            c.setCanonicalName(t[0]);
            c.setRoleType(CharacterRoleType.valueOf(t[1]));
            c.setGender(t[2]);
            c.setAgeRange(t[3]);
            c.setDescription(t[4]);
            c.setPersonality(Arrays.asList(t[5].split(",")));
            c.setAppearanceCount(Integer.parseInt(t[6]));
            c.setAliases(new ArrayList<>());
            c.setResolved(true);
            c.setCreatedAt(LocalDateTime.now());

            List<Character.Relationship> rels = new ArrayList<>();
            int ri = (i + seed) % relationships.length;
            Character.Relationship r = new Character.Relationship();
            r.setTarget(relationships[ri][0]);
            r.setRelation(relationships[ri][1]);
            rels.add(r);
            if (i > 0 && i % 2 == 0) {
                Character.Relationship r2 = new Character.Relationship();
                r2.setTarget(templates[(i + 1) % charCount][0]);
                r2.setRelation("故人");
                rels.add(r2);
            }
            c.setRelationships(rels);
            chars.add(c);
        }
        return chars;
    }

    private List<PlotEvent> buildPlotEventsMock(Long scriptId, List<Character> characters) {
        log.info("Using content-aware plot events for scriptId={}", scriptId);
        List<PlotEvent> events = new ArrayList<>();
        AtomicInteger idSeq = new AtomicInteger((int)(scriptId * 1000 + 500));

        // Generate events from chapter structure — each chapter = 1 event
        // This is far more relevant than hardcoded generic event names
        List<Chapter> chapters = null;
        try {
            Novel novel = novelService.findById(scriptId).orElse(null);
            if (novel != null) chapters = novel.getChapters();
        } catch (Exception ignored) {}

        if (chapters != null && !chapters.isEmpty()) {
            for (int i = 0; i < chapters.size(); i++) {
                Chapter ch = chapters.get(i);
                String content = ch.getContent();
                if (content == null || content.isBlank()) continue;

                PlotEvent e = new PlotEvent();
                e.setId((long) idSeq.getAndIncrement());
                e.setScriptId(scriptId);
                e.setEventOrder(i + 1);

                // Use chapter title or first line as event title
                String eventTitle = ch.getTitle();
                if (eventTitle == null || eventTitle.isBlank()) {
                    eventTitle = content.length() > 20 ? content.substring(0, 20) + "…" : content;
                }
                e.setTitle(eventTitle);

                // Description from content
                String desc = content.length() > 100 ? content.substring(0, 100) + "…" : content;
                e.setDescription(desc);

                e.setLocation(extractLocationFromText(content));
                e.setImportance(3);
                e.setChapterIds(List.of((long) ch.getChapterNumber()));
                e.setCharacterIds(characters.stream().limit(3).map(Character::getId).toList());
                events.add(e);
            }
        }

        if (events.isEmpty()) {
            // Absolute minimal fallback
            PlotEvent e = new PlotEvent();
            e.setId((long) idSeq.getAndIncrement());
            e.setScriptId(scriptId);
            e.setEventOrder(1);
            e.setTitle("故事开始");
            e.setDescription("故事的开端");
            e.setLocation("未知");
            e.setImportance(3);
            e.setChapterIds(List.of(1L));
            e.setCharacterIds(characters.stream().limit(2).map(Character::getId).toList());
            events.add(e);
        }
        return events;
    }

    private List<Scene> buildScenesMock(Long scriptId, List<Character> characters, Novel novel) {
        log.info("Using content-aware scene fallback for scriptId={}", scriptId);
        List<Scene> scenes = new ArrayList<>();
        List<Chapter> chapters = novel.getChapters();
        AtomicInteger idSeq = new AtomicInteger((int)(scriptId * 1000 + 100));
        int sceneNum = 0;

        if (chapters != null && !chapters.isEmpty()) {
            for (Chapter ch : chapters) {
                if (ch.getContent() == null || ch.getContent().isBlank()) continue;

                String content = ch.getContent();
                // Split chapter into segments by double-newline (natural paragraph breaks)
                String[] segments = content.split("\\n\\s*\\n");
                // Take up to 3 scenes per chapter
                int segmentsPerChapter = Math.min(segments.length, 3);

                for (int i = 0; i < segmentsPerChapter; i++) {
                    String segment = segments[i].trim();
                    if (segment.length() < 20) continue; // skip very short segments

                    sceneNum++;
                    Scene scene = new Scene();
                    scene.setId((long) idSeq.getAndIncrement());
                    scene.setScriptId(scriptId);
                    scene.setSceneNumber(sceneNum);
                    scene.setSourceReason(i == 0 ? SourceReason.CHAPTER_BOUNDARY : SourceReason.LOCATION);

                    // Extract location from segment text
                    String location = extractLocationFromText(segment);
                    scene.setLocation(location);

                    // Infer time of day
                    TimeOfDay tod = TimeOfDay.inferFromContent(segment);
                    scene.setTimeOfDay(tod != TimeOfDay.UNKNOWN ? tod : TimeOfDay.MORNING);

                    // Infer interior/exterior
                    scene.setInterior(inferInteriorFromText(location, segment));

                    // Title from first line or first 15 chars
                    String title = extractTitleFromText(segment, ch.getTitle());
                    scene.setTitle(title);

                    // Summary from first 80 chars
                    String summary = segment.length() > 80 ? segment.substring(0, 80) + "…" : segment;
                    scene.setSummary(summary);

                    // Mood from keywords
                    scene.setMood(inferMoodFromText(segment));

                    // Find which characters appear in this segment
                    List<Long> presentCharIds = new ArrayList<>();
                    for (Character c : characters) {
                        if (c.getCanonicalName() != null && segment.contains(c.getCanonicalName())) {
                            presentCharIds.add(c.getId());
                        }
                    }
                    if (presentCharIds.isEmpty() && !characters.isEmpty()) {
                        // At least include first 2 characters
                        presentCharIds.add(characters.get(0).getId());
                        if (characters.size() > 1) presentCharIds.add(characters.get(1).getId());
                    }
                    scene.setCharacterIds(presentCharIds);

                    scene.setChapterIds(List.of((long) ch.getChapterNumber()));
                    scene.setDialogues(new ArrayList<>());
                    scene.setActions(new ArrayList<>());
                    scene.setCreatedAt(LocalDateTime.now());
                    scenes.add(scene);
                }
            }
        }

        // If no content-based scenes could be generated, create minimal scenes
        if (scenes.isEmpty()) {
            log.warn("No content-based scenes generated — creating minimal placeholder scenes");
            int n = Math.min(characters.size(), 5);
            for (int i = 0; i < n; i++) {
                sceneNum++;
                Scene scene = new Scene();
                scene.setId((long) idSeq.getAndIncrement());
                scene.setScriptId(scriptId);
                scene.setSceneNumber(sceneNum);
                scene.setLocation("未知地点");
                scene.setTimeOfDay(i % 2 == 0 ? TimeOfDay.MORNING : TimeOfDay.AFTERNOON);
                scene.setInterior(true);
                scene.setTitle("场景 " + sceneNum);
                scene.setSummary("第" + sceneNum + "个场景");
                scene.setMood("中性");
                scene.setCharacterIds(List.of(characters.get(i % characters.size()).getId()));
                scene.setChapterIds(List.of(1L));
                scene.setSourceReason(SourceReason.CHAPTER_BOUNDARY);
                scene.setDialogues(new ArrayList<>());
                scene.setActions(new ArrayList<>());
                scene.setCreatedAt(LocalDateTime.now());
                scenes.add(scene);
            }
        }

        log.info("Content-aware fallback: generated {} scenes from {} chapters", scenes.size(),
                chapters != null ? chapters.size() : 0);
        return scenes;
    }

    /** Extract location from text by looking for place-indicating patterns */
    private String extractLocationFromText(String text) {
        // Look for location patterns like "在XX", "来到XX", "走进XX"
        java.util.regex.Pattern locPtn = java.util.regex.Pattern.compile(
                "(?:在|来到|走进|进入|回到|前往|穿过)([^，。；,!]{2,8})(?:，|。|；|,|\\s|$)");
        java.util.regex.Matcher m = locPtn.matcher(text);
        if (m.find()) {
            String loc = m.group(1).trim();
            if (!loc.isEmpty() && loc.length() <= 15) return loc;
        }
        return "未知地点";
    }

    /** Extract a scene title from text segment */
    private String extractTitleFromText(String text, String chapterTitle) {
        if (text.length() <= 15) return text;
        // Use first sentence or first 15 chars
        int end = text.indexOf('。');
        if (end == -1) end = text.indexOf('，');
        if (end == -1) end = Math.min(15, text.length());
        String title = text.substring(0, end).trim();
        if (title.length() > 12) title = title.substring(0, 12) + "…";
        return title;
    }

    /** Infer interior/exterior from location name and content */
    private boolean inferInteriorFromText(String location, String text) {
        String combined = (location + " " + text).toLowerCase();
        String[] indoorKw = {"室", "房", "屋", "厅", "堂", "店", "馆", "院", "宫内", "殿", "楼内", "房间", "卧室", "客厅", "厨房", "教室", "办公室"};
        String[] outdoorKw = {"街", "路", "场", "外", "野", "山", "海", "林", "园", "广场", "操场", "公园", "森林", "河边", "湖边", "海岸"};
        int indoor = 0, outdoor = 0;
        for (String kw : indoorKw) if (combined.contains(kw)) indoor++;
        for (String kw : outdoorKw) if (combined.contains(kw)) outdoor++;
        return indoor >= outdoor;
    }

    /** Infer mood from text keywords */
    private String inferMoodFromText(String text) {
        if (text.contains("惊") || text.contains("怕") || text.contains("恐")) return "紧张";
        if (text.contains("笑") || text.contains("温暖") || text.contains("开心")) return "温馨";
        if (text.contains("怒") || text.contains("恨") || text.contains("杀")) return "愤怒";
        if (text.contains("哭") || text.contains("泪") || text.contains("悲伤")) return "悲伤";
        if (text.contains("神秘") || text.contains("秘密") || text.contains("黑影")) return "悬疑";
        return "中性";
    }

    /** Generate mock dialogues for a SINGLE scene (does not overwrite other scenes). */
    private List<Dialogue> buildMockDialoguesForScene(Scene scene, List<Character> characters) {
        String[][] emotionLines = {
            {"CALM", "嗯，我知道了。"},
            {"SURPRISED", "什么？这是真的吗？"},
            {"ANXIOUS", "我们必须尽快行动。"},
            {"CALM", "说说你的想法。"},
            {"COLD", "你以为这就能阻止我吗？"},
            {"ANGRY", "你根本不明白这意味着什么！"},
            {"FEARFUL", "我……我不知道该怎么办。"},
            {"PROUD", "我绝不会放弃的。"},
            {"CALM", "那就这样决定了。"},
            {"GENTLE", "一切都会好起来的。"},
        };
        List<Dialogue> dialogues = new ArrayList<>();
        if (characters == null || characters.isEmpty()) return dialogues;
        List<String> names = characters.stream()
                .map(com.novel2script.domain.model.Character::getCanonicalName)
                .filter(n -> n != null && !n.isBlank()).toList();
        if (names.isEmpty()) return dialogues;
        int count = 2;
        for (int j = 0; j < count; j++) {
            String speaker = names.get(j % names.size());
            Dialogue d = new Dialogue();
            d.setSceneId(scene.getId());
            d.setSequence(j + 1);
            d.setSpeaker(speaker);
            d.setEmotion(com.novel2script.common.enums.Emotion.valueOf(emotionLines[j][0]));
            d.setContent(emotionLines[j][1]);
            d.setCreatedAt(java.time.LocalDateTime.now());
            d.setCharacterId(characters.get(j % characters.size()).getId());
            dialogues.add(d);
        }
        return dialogues;
    }

    /** Generate mock actions for a SINGLE scene (does not overwrite other scenes). */
    private List<com.novel2script.domain.model.Action> buildMockActionsForScene(Scene scene, List<Character> characters) {
        String[][] templates = {
            {"ACTION", "缓缓推开门，警惕地环顾四周"},
            {"REACTION", "惊讶地后退了一步"},
            {"BEAT", "沉默片刻，深吸一口气"},
            {"ACTION", "转身走向门口"},
        };
        List<com.novel2script.domain.model.Action> actions = new ArrayList<>();
        int count = 2;
        for (int j = 0; j < count; j++) {
            String[] at = templates[j % templates.length];
            com.novel2script.domain.model.Action a = new com.novel2script.domain.model.Action();
            a.setSceneId(scene.getId());
            a.setSequence(j + 1);
            a.setActionType(at[0]);
            a.setDescription(at[1]);
            a.setDurationMs(1500);
            a.setCreatedAt(java.time.LocalDateTime.now());
            if (characters != null && !characters.isEmpty()) {
                a.setCharacterId(characters.get(j % characters.size()).getId());
            }
            actions.add(a);
        }
        return actions;
    }

    private List<Scene> buildDialoguesMock(List<Scene> scenes, List<Character> characters) {
        // Use actual character names from the resolved character list
        // so speaker names always match even in mock fallback mode
        String[][] emotionLines = {
            {"CALM", "嗯，我知道了。"},
            {"SURPRISED", "什么？这是真的吗？"},
            {"ANXIOUS", "我们必须尽快行动。"},
            {"CALM", "说说你的想法。"},
            {"COLD", "你以为这就能阻止我吗？"},
            {"ANGRY", "你根本不明白这意味着什么！"},
            {"FEARFUL", "我……我不知道该怎么办。"},
            {"PROUD", "我绝不会放弃的。"},
            {"CALM", "那就这样决定了。"},
            {"GENTLE", "一切都会好起来的。"},
        };

        if (characters == null || characters.isEmpty()) {
            log.warn("No characters available for mock dialogues");
            return scenes;
        }

        // Build a rotating speaker list from actual characters
        List<String> speakerNames = characters.stream()
                .map(Character::getCanonicalName)
                .filter(n -> n != null && !n.isBlank())
                .toList();
        if (speakerNames.isEmpty()) return scenes;

        AtomicInteger dId = new AtomicInteger(1);
        for (int si = 0; si < scenes.size(); si++) {
            Scene scene = scenes.get(si);
            List<Dialogue> dialogues = new ArrayList<>();
            int count = 2 + (si % 2);
            for (int j = 0; j < count; j++) {
                int lineIdx = (si * 3 + j) % emotionLines.length;
                int speakerIdx = (si + j) % speakerNames.size();
                String speaker = speakerNames.get(speakerIdx);

                Dialogue d = new Dialogue();
                d.setId((long) dId.getAndIncrement());
                d.setSceneId(scene.getId());
                d.setSequence(j + 1);
                d.setSpeaker(speaker);
                d.setEmotion(Emotion.valueOf(emotionLines[lineIdx][0]));
                d.setContent(emotionLines[lineIdx][1]);
                d.setCreatedAt(LocalDateTime.now());

                // Map speaker name to character ID
                for (Character c : characters) {
                    if (speaker.equals(c.getCanonicalName())) {
                        d.setCharacterId(c.getId());
                        break;
                    }
                }
                dialogues.add(d);
            }
            scene.setDialogues(dialogues);
        }
        return scenes;
    }

    private List<Scene> buildActionsMock(List<Scene> scenes, List<Character> characters) {
        String[][] templates = {
            {"ACTION", "缓缓推开门，警惕地环顾四周"},
            {"REACTION", "惊讶地后退了一步"},
            {"ACTION", "快步走向窗户，向外张望"},
            {"BEAT", "沉默片刻，深吸一口气"},
            {"BUSINESS", "从口袋里掏出手机查看"},
            {"ACTION", "用力拍桌而起"},
            {"REACTION", "眼神中闪过一道光芒"},
            {"BUSINESS", "取出手帕擦拭额头的汗水"},
            {"ACTION", "缓缓举起手示意"},
            {"REACTION", "身子微微一颤"},
            {"BEAT", "两人相视无言"},
            {"ACTION", "转身走向门口"},
        };

        AtomicInteger aId = new AtomicInteger(10000);
        for (int si = 0; si < scenes.size(); si++) {
            Scene scene = scenes.get(si);
            List<Action> actions = new ArrayList<>();
            int count = 1 + (si % 2);
            for (int j = 0; j < count; j++) {
                String[] at = templates[(si * 2 + j) % templates.length];
                Action a = new Action();
                a.setId((long) aId.getAndIncrement());
                a.setSceneId(scene.getId());
                a.setSequence(j + 1);
                a.setActionType(at[0]);
                a.setDescription(at[1]);
                a.setDurationMs(1500 + (int)(Math.random() * 2000));
                a.setCreatedAt(LocalDateTime.now());
                if (!scene.getCharacterIds().isEmpty() && Math.random() > 0.3) {
                    a.setCharacterId(scene.getCharacterIds().get(0));
                }
                actions.add(a);
            }
            scene.setActions(actions);
        }
        return scenes;
    }
}
