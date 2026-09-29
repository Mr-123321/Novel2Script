package com.novel2script.application.service.workflow;

import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.application.service.agent.model.CompositionInput;
import com.novel2script.application.service.exporter.YamlExporter;
import com.novel2script.application.service.ScriptService;
import com.novel2script.application.service.workflow.model.Step;
import com.novel2script.application.service.workflow.model.Workflow;
import com.novel2script.common.enums.ContentSource;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.GenerationStatus;
import com.novel2script.common.enums.WorkflowStep;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Novel;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pre-built workflow definitions wiring real agents into {@link WorkflowStep} actions.
 * <p>
 * W13: 每个 step 的 action 从 "[NO-OP] placeholder" 改为对真实 Agent 的调用，
 * 输入/输出全部经由 {@link GenerationContext} 传递，Agent 实例经
 * {@link AgentBundle} 打包注入（方法参数，<b>绝不存为字段</b> ——
 * 本类是 Spring 单例，存 ctx 会导致多剧本并发串数据）。
 * <p>
 * <b>NOTE(已接入，W14/W15)</b>: 本类定义的工作流自 W14 起由
 * {@code GenerationOrchestrator#runMultiStepPipeline} 在主链路调用；
 * W15 起各步骤完成时经 {@code ScriptService.updateProgress} 推送主链路进度
 * （→ {@code ScriptProgressChangedEvent} → SSE）。
 *
 * <h3>异常约定（工单注意点 3）</h3>
 * 不吞异常 —— {@code Step.action} 抛异常才会触发引擎重试，catch 后返回空集合
 * 会让引擎误判成功。各阶段的空结果一律 {@code throw IllegalStateException}。
 * 唯二例外（均为已验证的显式降级，非伪装成功）：
 * <ol>
 *   <li>{@code CHARACTER_RESOLVE} 消歧失败 → 降级用原始提取结果
 *       （与主链路 {@code GenerationOrchestrator} 相同的降级路径，不编造新角色）；</li>
 *   <li>{@code DIALOGUE_GENERATE} / {@code ACTION_GENERATE} 的场景级三层兜底
 *       —— AI 失败走正则抽取，再失败显式标记 {@code GenerationStatus.FAILED}
 *       并递增 {@code ctx} 失败计数（场景级失败不适合整步骤重试，
 *       那会重做全部场景；失败事实由 ctx 计数与场景级 status 如实承载）。</li>
 * </ol>
 */
@Slf4j
@Component
public class WorkflowDefinitions {

    /** Regex-fallback dialogue ID range — distinct from AI-generated IDs (W12 语义). */
    private final AtomicLong dialogueIdSeq = new AtomicLong(60000);

    private final YamlExporter yamlExporter;

    /**
     * W15: 主链路进度推送出口（Script.progress + workflowState.currentStep 落库，
     * 并发布 ScriptProgressChangedEvent → SSE）。无循环依赖：
     * ScriptService → {@code @Lazy} Orchestrator → 本类，唯一的回边已被 @Lazy 打断。
     */
    private final ScriptService scriptService;

    public WorkflowDefinitions(YamlExporter yamlExporter, ScriptService scriptService) {
        this.yamlExporter = yamlExporter;
        this.scriptService = scriptService;
    }

    /**
     * The full 9-step script-generation workflow with all dependencies,
     * wired to real agents operating on the shared {@link GenerationContext}.
     *
     * <pre>
     *   CHAPTER_PARSE
     *    ├── CHARACTER_EXTRACT
     *    │    └── CHARACTER_RESOLVE ──────────┐
     *    ├── PLOT_EXTRACT                     │
     *    │    └── SCENE_SEGMENT               │
     *    │         ├── DIALOGUE_GENERATE ─────┤   (parallel with ACTION_GENERATE;
     *    │         └── ACTION_GENERATE ───────┘    both mutate the same Scene objects)
     *    └── SCRIPT_COMPOSE   (waits for all parallel branches)
     *         └── YAML_EXPORT
     * </pre>
     *
     * @param ctx    per-execution shared context — <b>method argument only</b>, never stored
     * @param agents bundled real agents
     * @return the fully-wired workflow definition
     */
    public Workflow fullGenerationWorkflow(GenerationContext ctx, AgentBundle agents) {
        return Workflow.builder()
                .name("fullGeneration")
                .description("Full pipeline: novel → chapters → characters → plot → scenes → dialogue/action (parallel) → composition → YAML")
                .steps(List.of(
                        step(WorkflowStep.CHAPTER_PARSE, () -> chapterParse(ctx)),

                        step(WorkflowStep.CHARACTER_EXTRACT, WorkflowStep.CHAPTER_PARSE,
                                () -> characterExtract(ctx, agents)),
                        step(WorkflowStep.CHARACTER_RESOLVE, WorkflowStep.CHARACTER_EXTRACT,
                                () -> characterResolve(ctx, agents)),

                        step(WorkflowStep.PLOT_EXTRACT, WorkflowStep.CHAPTER_PARSE,
                                () -> plotExtract(ctx, agents)),
                        step(WorkflowStep.SCENE_SEGMENT, WorkflowStep.PLOT_EXTRACT,
                                () -> sceneSegment(ctx, agents)),

                        step(WorkflowStep.DIALOGUE_GENERATE, WorkflowStep.SCENE_SEGMENT,
                                () -> dialogueGenerate(ctx, agents)),
                        step(WorkflowStep.ACTION_GENERATE, WorkflowStep.SCENE_SEGMENT,
                                () -> actionGenerate(ctx, agents)),

                        step(WorkflowStep.SCRIPT_COMPOSE,
                                List.of(WorkflowStep.CHARACTER_RESOLVE,
                                        WorkflowStep.DIALOGUE_GENERATE,
                                        WorkflowStep.ACTION_GENERATE),
                                () -> scriptCompose(ctx, agents)),

                        step(WorkflowStep.YAML_EXPORT, WorkflowStep.SCRIPT_COMPOSE,
                                () -> yamlExport(ctx))
                ))
                .build();
    }

    // ═══════════════════════════════════════════════════════
    //  Step actions — real agent calls on GenerationContext
    // ═══════════════════════════════════════════════════════

    /** CHAPTER_PARSE: 章节在上传时已由 ChapterParser 解析入库，此处取用并校验。 */
    private void chapterParse(GenerationContext ctx) {
        ensureNotCancelled(ctx);
        Novel novel = ctx.getNovel();
        if (novel == null) {
            throw new IllegalStateException("GenerationContext.novel is null — cannot parse chapters");
        }
        List<Chapter> chapters = novel.getChapters();
        if (chapters == null || chapters.isEmpty()) {
            // 抛异常 → 引擎重试耗尽 → 步骤 FAILED。不伪造章节。
            throw new IllegalStateException(
                    "Novel '" + novel.getTitle() + "' has no parsed chapters");
        }
        ctx.setChapters(chapters);
        log.info("[workflow] CHAPTER_PARSE: {} chapters from '{}'", chapters.size(), novel.getTitle());
        reportProgress(ctx, WorkflowStep.CHAPTER_PARSE, 15);
    }

    /** CHARACTER_EXTRACT: AI 角色提取。空结果抛异常触发重试。 */
    private void characterExtract(GenerationContext ctx, AgentBundle agents) {
        ensureNotCancelled(ctx);
        List<String> focusCharacters = ctx.getAttribute("focusCharacters");
        List<CharacterExtractionResult> results =
                agents.characterAgent().extract(ctx.getChapters(),
                        focusCharacters != null ? focusCharacters : List.of());
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException("AI 角色提取返回空结果（请检查 API Key / 模型可用性）");
        }
        ctx.setRawCharacters(results);
        log.info("[workflow] CHARACTER_EXTRACT: {} raw characters", results.size());
        reportProgress(ctx, WorkflowStep.CHARACTER_EXTRACT, 25);
    }

    /** CHARACTER_RESOLVE: 角色消歧；失败降级用原始提取结果（与主链路一致，不编造）。 */
    private void characterResolve(GenerationContext ctx, AgentBundle agents) {
        ensureNotCancelled(ctx);
        List<CharacterExtractionResult> raw = ctx.getRawCharacters();
        List<Character> characters;
        try {
            characters = agents.characterResolverAgent().resolve(raw);
        } catch (Exception e) {
            log.warn("[workflow] 角色消歧失败，降级使用原始提取结果: {}", e.getMessage());
            characters = raw.stream().map(CharacterExtractionResult::toDomainCharacter).toList();
        }
        if (characters == null || characters.isEmpty()) {
            throw new IllegalStateException("角色解析结果为空");
        }
        long scriptId = ctx.getScriptId() != null ? ctx.getScriptId() : 0L;
        AtomicInteger seq = new AtomicInteger((int) (scriptId * 1000));
        for (Character c : characters) {
            if (c.getId() == null) c.setId((long) seq.getAndIncrement());
            c.setScriptId(scriptId);
        }
        ctx.setCharacters(characters);
        log.info("[workflow] CHARACTER_RESOLVE: {} characters", characters.size());
        reportProgress(ctx, WorkflowStep.CHARACTER_RESOLVE, 35);
    }

    /** PLOT_EXTRACT: 情节提取（辅助上下文，允许为空但异常会触发重试）。 */
    private void plotExtract(GenerationContext ctx, AgentBundle agents) {
        ensureNotCancelled(ctx);
        List<PlotEvent> events = agents.plotExtractionAgent()
                .extract(ctx.getChapters(), ctx.getCharacters());
        ctx.setPlotEvents(events);
        log.info("[workflow] PLOT_EXTRACT: {} plot events", ctx.getPlotEvents().size());
        reportProgress(ctx, WorkflowStep.PLOT_EXTRACT, 45);
    }

    /** SCENE_SEGMENT: 场景切分 + ID 分配 + 初始化对白/动作空列表。 */
    private void sceneSegment(GenerationContext ctx, AgentBundle agents) {
        ensureNotCancelled(ctx);
        List<Scene> scenes = agents.sceneAgent()
                .segment(ctx.getChapters(), ctx.getPlotEvents(), ctx.getCharacters());
        if (scenes == null || scenes.isEmpty()) {
            throw new IllegalStateException("AI 场景切分返回空结果");
        }
        long scriptId = ctx.getScriptId() != null ? ctx.getScriptId() : 0L;
        AtomicInteger seq = new AtomicInteger((int) (scriptId * 1000 + 100));
        for (Scene s : scenes) {
            if (s.getId() == null) s.setId((long) seq.getAndIncrement());
            s.setScriptId(scriptId);
            if (s.getDialogues() == null) s.setDialogues(new ArrayList<>());
            if (s.getActions() == null) s.setActions(new ArrayList<>());
        }
        ctx.setScenes(scenes);
        log.info("[workflow] SCENE_SEGMENT: {} scenes", scenes.size());
        reportProgress(ctx, WorkflowStep.SCENE_SEGMENT, 55);
    }

    /**
     * DIALOGUE_GENERATE: 场景级三层兜底（AI → 正则抽取 → 显式失败），
     * 与主链路 {@code GenerationOrchestrator} 语义一致。
     * <p>
     * ⚠️ 本步骤与 ACTION_GENERATE 被引擎并行调度且同写 Scene 对象 ——
     * 所有 Scene 突变必须 {@code synchronized (scene)}。
     * 场景级失败不抛异常（整步骤重试会重做全部场景），
     * 而是显式 {@code GenerationStatus.FAILED} + ctx 失败计数如实承载。
     */
    private void dialogueGenerate(GenerationContext ctx, AgentBundle agents) {
        ensureNotCancelled(ctx);
        List<Scene> scenes = ctx.getScenes();
        List<Character> characters = ctx.getCharacters();

        if (characters == null || characters.isEmpty()) {
            // 无角色不可归因台词 —— 与主链路一致：显式失败，不编造
            for (Scene scene : scenes) {
                synchronized (scene) {
                    scene.setDialogues(new ArrayList<>());
                    scene.setDialogueStatus(GenerationStatus.FAILED);
                }
                ctx.incrementFailedDialogueScenes();
            }
            log.warn("[workflow] 无角色可用于对白归因，{} 个场景显式标记失败", scenes.size());
            reportProgress(ctx, WorkflowStep.DIALOGUE_GENERATE, 70);
            return;
        }

        agents.dialogueAgent().invalidateProfileCache();

        Scene prevScene = null;
        List<Dialogue> prevDialogues = null;
        for (Scene scene : scenes) {
            List<Character> presentChars = resolveCharactersForScene(scene, characters);

            // Tier 1: AI 生成（带前一场景对白保证叙事连续性）
            List<Dialogue> dialogues = null;
            try {
                dialogues = agents.dialogueAgent().generate(
                        scene, presentChars, List.of(), prevScene, null, prevDialogues);
            } catch (Exception e) {
                log.debug("[workflow] AI 对白失败 '{}': {}", scene.getTitle(), e.getMessage());
            }
            if (dialogues != null && !dialogues.isEmpty()) {
                markDialogueSource(dialogues, ContentSource.AI);
                synchronized (scene) {
                    scene.setDialogues(dialogues);
                    scene.setDialogueStatus(GenerationStatus.COMPLETED);
                }
                prevScene = scene;
                prevDialogues = dialogues;
                continue;
            }

            // Tier 2: 正则抽取（W01/W02 语义：来源 REGEX，不冒充 AI）
            dialogues = extractDialoguesFromContext(scene, presentChars);
            if (!dialogues.isEmpty()) {
                markDialogueSource(dialogues, ContentSource.REGEX);
                synchronized (scene) {
                    scene.setDialogues(dialogues);
                    scene.setDialogueStatus(GenerationStatus.COMPLETED);
                }
                prevScene = scene;
                prevDialogues = dialogues;
                continue;
            }

            // Tier 3: 显式失败 —— 不编造，场景留空待人工补全
            synchronized (scene) {
                scene.setDialogues(new ArrayList<>());
                scene.setDialogueStatus(GenerationStatus.FAILED);
            }
            ctx.incrementFailedDialogueScenes();
            log.warn("[workflow] 对白生成失败，场景 '{}' 标记为待补全（不编造台词）", scene.getTitle());
        }

        int failed = ctx.getFailedDialogueScenes();
        if (failed > 0) {
            log.warn("[workflow] 对白生成：{} 个场景无对白（AI 与原文抽取均失败）——不编造，待人工补全", failed);
        }
        reportProgress(ctx, WorkflowStep.DIALOGUE_GENERATE, 70);
    }

    /**
     * ACTION_GENERATE: 动作生成（仅 AI，无正则兜底 —— W01/W02 语义）。
     * 与 DIALOGUE_GENERATE 并行执行，Scene 突变同样 {@code synchronized (scene)}。
     */
    private void actionGenerate(GenerationContext ctx, AgentBundle agents) {
        ensureNotCancelled(ctx);
        List<Scene> scenes = ctx.getScenes();
        List<Character> characters = ctx.getCharacters();
        List<Dialogue> allDialogues = flattenDialogues(scenes);

        if (characters == null || characters.isEmpty()) {
            for (Scene scene : scenes) {
                synchronized (scene) {
                    scene.setActions(new ArrayList<>());
                    scene.setActionStatus(GenerationStatus.FAILED);
                }
                ctx.incrementFailedActionScenes();
            }
            log.warn("[workflow] 无角色可用于动作归因，{} 个场景显式标记失败", scenes.size());
            reportProgress(ctx, WorkflowStep.ACTION_GENERATE, 80);
            return;
        }

        for (Scene scene : scenes) {
            List<Character> presentChars = resolveCharactersForScene(scene, characters);
            List<Dialogue> sceneDialogues = allDialogues.stream()
                    .filter(d -> d.getSceneId() != null && d.getSceneId().equals(scene.getId()))
                    .toList();

            List<Action> actions = null;
            try {
                actions = agents.actionAgent().generate(scene, sceneDialogues, presentChars);
            } catch (Exception e) {
                log.debug("[workflow] AI 动作失败 '{}': {}", scene.getTitle(), e.getMessage());
            }

            if (actions != null && !actions.isEmpty()) {
                markActionSource(actions, ContentSource.AI);
                synchronized (scene) {
                    scene.setActions(actions);
                    scene.setActionStatus(GenerationStatus.COMPLETED);
                }
            } else {
                // 无正则兜底（动作仅 AI）—— 显式失败，不编造
                synchronized (scene) {
                    scene.setActions(new ArrayList<>());
                    scene.setActionStatus(GenerationStatus.FAILED);
                }
                ctx.incrementFailedActionScenes();
                log.warn("[workflow] 动作生成失败，场景 '{}' 标记为待补全（不编造动作）", scene.getTitle());
            }
        }
        reportProgress(ctx, WorkflowStep.ACTION_GENERATE, 80);
    }

    /** SCRIPT_COMPOSE: 聚合全部产出合成剧本，结果挂到 ctx attributes。 */
    private void scriptCompose(GenerationContext ctx, AgentBundle agents) {
        ensureNotCancelled(ctx);
        Novel novel = ctx.getNovel();
        CompositionInput input = new CompositionInput(
                novel != null ? novel.getId() : null,
                novel != null ? novel.getTitle() : null,
                ctx.getCharacters(),
                ctx.getScenes(),
                flattenDialogues(ctx.getScenes()),
                flattenActions(ctx.getScenes()),
                ctx.getPlotEvents(),
                ctx.getChapters());

        if (!input.isValid()) {
            throw new IllegalStateException("CompositionInput 缺少必要数据（characters/scenes 为空）");
        }
        Script composed = agents.scriptComposer().compose(input);
        if (composed == null) {
            throw new IllegalStateException("ScriptComposer 返回 null");
        }
        ctx.setAttribute("composedScript", composed);
        log.info("[workflow] SCRIPT_COMPOSE: '{}' ({} scenes)", composed.getTitle(),
                composed.getScenes() != null ? composed.getScenes().size() : 0);
        reportProgress(ctx, WorkflowStep.SCRIPT_COMPOSE, 92);
    }

    /** YAML_EXPORT: 导出合成剧本为 YAML，挂到 ctx attributes。 */
    private void yamlExport(GenerationContext ctx) {
        ensureNotCancelled(ctx);
        Script composed = ctx.getAttribute("composedScript");
        if (composed == null) {
            throw new IllegalStateException("无可导出的剧本（SCRIPT_COMPOSE 未产出 composedScript）");
        }
        String yaml = yamlExporter.exportToString(composed);
        ctx.setAttribute("yamlContent", yaml);
        log.info("[workflow] YAML_EXPORT: {} chars", yaml.length());
        reportProgress(ctx, WorkflowStep.YAML_EXPORT, 100);
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    /**
     * W17: 用户取消生成检查 —— 每个步骤入口调用。取消后（剧本已删、标记已登记）
     * 立即抛出中止本步骤，不再消耗 AI 调用；引擎重试耗尽后整体 FAILED，
     * 流水线终止，后续写回因剧本行已删除全部为 no-op。
     *
     * <p>scriptService 为 null 时（单测直连构造，见 WorkflowEngineTest）跳过检查。
     */
    private void ensureNotCancelled(GenerationContext ctx) {
        if (scriptService != null && scriptService.isGenerationCancelled(ctx.getScriptId())) {
            throw new java.util.concurrent.CancellationException(
                    "用户已取消生成（scriptId=" + ctx.getScriptId() + "）");
        }
    }

    /** Per-execution monotonic progress guard (stored in ctx attributes). */
    private static final String MAX_PROGRESS_KEY = "maxProgressPct";

    /**
     * W15: 推送主链路进度 —— 步骤完成时按固定百分比写入 Script.progress 与
     * workflowState.currentStep，并经 {@code ScriptProgressChangedEvent} → SSE
     * 到前端进度条。
     *
     * <p>⚠️ 并行分支（CHARACTER_EXTRACT↔PLOT_EXTRACT、DIALOGUE_GENERATE↔
     * ACTION_GENERATE）完成顺序不定，进度只允许<b>单调不减</b>：
     * 低百分比的迟到推送被丢弃，否则前端进度条会回跳。
     * <p>异常不外抛 —— 进度推送是旁路，失败不阻断生成主流程。
     */
    private void reportProgress(GenerationContext ctx, WorkflowStep step, double pct) {
        Long scriptId = ctx.getScriptId();
        if (scriptId == null || scriptService == null) return;
        try {
            AtomicInteger max;
            synchronized (ctx) {
                max = ctx.getAttribute(MAX_PROGRESS_KEY);
                if (max == null) {
                    max = new AtomicInteger(0);
                    ctx.setAttribute(MAX_PROGRESS_KEY, max);
                }
            }
            int prev;
            do {
                prev = max.get();
                if ((int) pct <= prev) {
                    log.debug("[workflow] 丢弃回跳进度 {}% ({}), 当前已达 {}%", pct, step, prev);
                    return;
                }
            } while (!max.compareAndSet(prev, (int) pct));
            scriptService.updateProgress(scriptId, pct, step);
            log.info("[workflow] 主链路进度: {} → {}%", step.getAgentName(), pct);
        } catch (Exception e) {
            log.warn("[workflow] 进度推送失败（不阻断生成）: {}", e.getMessage());
        }
    }

    private static List<Dialogue> flattenDialogues(List<Scene> scenes) {
        if (scenes == null) return List.of();
        return scenes.stream()
                .flatMap(s -> s.getDialogues() == null
                        ? Stream.<Dialogue>empty() : s.getDialogues().stream())
                .toList();
    }

    private static List<Action> flattenActions(List<Scene> scenes) {
        if (scenes == null) return List.of();
        return scenes.stream()
                .flatMap(s -> s.getActions() == null
                        ? Stream.<Action>empty() : s.getActions().stream())
                .toList();
    }

    /**
     * Resolve which characters are present in a scene — same semantics as
     * {@code GenerationOrchestrator#resolveCharactersForScene} (runtime helper,
     * falls back to ALL characters so AI can decide who speaks).
     */
    private static List<Character> resolveCharactersForScene(Scene scene, List<Character> allCharacters) {
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
        return new ArrayList<>(allCharacters);
    }

    /** Stamp dialogue provenance (named per type — lists erase to same signature). */
    private static void markDialogueSource(List<Dialogue> dialogues, ContentSource source) {
        if (dialogues == null) return;
        for (Dialogue d : dialogues) {
            if (d != null) d.setSource(source);
        }
    }

    /** Stamp action provenance. */
    private static void markActionSource(List<Action> actions, ContentSource source) {
        if (actions == null) return;
        for (Action a : actions) {
            if (a != null) a.setSource(source);
        }
    }

    /**
     * W01/W02 改造后的正则兜底（自 {@code GenerationOrchestrator#extractDialoguesFromContext}
     * 迁入，语义一致）：从场景 summary 抽取"说话动词"模式，说话人必须能匹配到已知角色，
     * 不强行归因代词/群称 —— 来源标记为 {@link ContentSource#REGEX}。
     * ID 使用独立区间（60000+），避免与 AI 生成 ID 冲突。
     */
    private List<Dialogue> extractDialoguesFromContext(Scene scene, List<Character> presentCharacters) {
        List<Dialogue> dialogues = new ArrayList<>();

        String summary = scene.getSummary();
        if (summary == null || summary.isBlank()) return dialogues;

        // Direct-speech pattern for Chinese text:
        //   group 1: speaker name (1-6 chars before speech verb)
        //   group 2: speech content (2-60 chars after speech marker)
        Pattern speechPattern = Pattern.compile(
                "([^：:\"'\"'「『\\s]{1,6})" +                            // speaker name
                "(?:冷冷|淡淡|低声|大声|轻声|小声|怒|笑|哭|吼|喊)?" +      // optional modifier
                "(?:说道|说道：|说：|说|道：|道|喊道|问道|答道|回道|答|问)" + // speech verb
                "[：:\"'\"『「]?" +                                       // optional opening quote
                "(.{2,60})" +                                            // speech content
                "[\"'\"」』]?");                                          // optional closing quote
        Matcher m = speechPattern.matcher(summary);

        // Noise words that are clearly not character names
        Set<String> noiseSpeakers = Set.of(
                "他", "她", "它", "我", "你", "您", "俺", "咱", "谁",
                "一人", "两人", "三人", "众人", "大家", "有人", "某人",
                "来人", "旁人", "别人", "那人", "这人", "此人",
                "一个", "这个", "那个", "哪个"
        );

        int seq = 0;
        while (m.find() && seq < 10) {
            String speakerName = m.group(1).trim();
            if (speakerName.length() <= 1 || noiseSpeakers.contains(speakerName)) {
                continue;
            }
            String content = (m.groupCount() >= 2 && m.group(2) != null)
                    ? m.group(2).trim() : "";

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
            // 不强行归因 —— 说话人匹配不到已知角色时跳过
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
            log.info("[workflow] 从场景 '{}' 上下文抽取 {} 条对白（正则兜底）",
                    scene.getTitle(), dialogues.size());
        }
        return dialogues;
    }

    // ═══════════════════════════════════════════════════════
    //  Step factory helpers
    // ═══════════════════════════════════════════════════════

    private static Step step(WorkflowStep type, Runnable action) {
        return Step.builder()
                .stepType(type)
                .dependsOn(List.of())
                .maxRetries(type.isRetryable() ? 3 : 0)
                .onFailure(type.isRetryable() ? "RETRY" : "ABORT")
                .action(action)
                .build();
    }

    private static Step step(WorkflowStep type, WorkflowStep dep, Runnable action) {
        return Step.builder()
                .stepType(type)
                .dependsOn(List.of(dep))
                .maxRetries(type.isRetryable() ? 3 : 0)
                .onFailure(type.isRetryable() ? "RETRY" : "ABORT")
                .action(action)
                .build();
    }

    private static Step step(WorkflowStep type, List<WorkflowStep> deps, Runnable action) {
        return Step.builder()
                .stepType(type)
                .dependsOn(deps)
                .maxRetries(type.isRetryable() ? 3 : 0)
                .onFailure(type.isRetryable() ? "RETRY" : "ABORT")
                .action(action)
                .build();
    }
}
