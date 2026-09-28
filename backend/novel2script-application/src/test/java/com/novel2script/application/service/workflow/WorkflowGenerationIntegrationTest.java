package com.novel2script.application.service.workflow;

import com.novel2script.application.service.GenerationOrchestrator;
import com.novel2script.application.service.NovelService;
import com.novel2script.application.service.ScriptService;
import com.novel2script.application.service.agent.ActionAgent;
import com.novel2script.application.service.agent.CharacterAgent;
import com.novel2script.application.service.agent.CharacterResolverAgent;
import com.novel2script.application.service.agent.DialogueAgent;
import com.novel2script.application.service.agent.PlotExtractionAgent;
import com.novel2script.application.service.agent.SceneAgent;
import com.novel2script.application.service.agent.ScriptComposer;
import com.novel2script.application.service.agent.ScriptGenerationAgent;
import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.application.service.exporter.YamlExporter;
import com.novel2script.application.service.workflow.model.StepStatus;
import com.novel2script.application.service.workflow.model.Workflow;
import com.novel2script.application.service.workflow.model.WorkflowProgress;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.WorkflowStep;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Novel;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import com.novel2script.infrastructure.config.AiModelRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * W16 集成测试：GenerationOrchestrator → WorkflowEngine → WorkflowDefinitions
 * 全链路（Agent 层 mock，不依赖数据库与 AI API）。
 *
 * <p>覆盖两个核心场景：
 * <ol>
 *   <li>9 步工作流全部成功：每步 COMPLETED，依赖顺序正确
 *       （CHARACTER_EXTRACT 在 CHAPTER_PARSE 之后；SCRIPT_COMPOSE 在
 *       CHARACTER_RESOLVE / DIALOGUE / ACTION 之后），且 DIALOGUE 与
 *       ACTION 的执行窗口重叠（并行证据）；</li>
 *   <li>SCENE_SEGMENT 失败：SCENE_SEGMENT=FAILED，其传递依赖方
 *       （DIALOGUE / ACTION / SCRIPT_COMPOSE / YAML_EXPORT）显式 SKIPPED，
 *       上游步骤不受影响，整体终态 FAILED。</li>
 * </ol>
 *
 * <p><b>等待机制</b>：{@code launchGeneration} 是 fire-and-forget（内部
 * executor 异步执行，无返回句柄），测试通过 spy 捕获引擎返回的
 * {@code CompletableFuture<String>} 拿到 executionId，并在终态落库调用
 * （completeScript / markFailed）上用 Mockito timeout 等待。
 */
@DisplayName("W16: 工作流生成全链路集成测试（Agent mock）")
class WorkflowGenerationIntegrationTest {

    private static final Long SCRIPT_ID = 1L;
    private static final Long NOVEL_ID = 10L;
    private static final long WAIT_MS = 30_000;

    // ── mock 层（AI Agent + 持久化边界）──
    private ScriptService scriptService;
    private NovelService novelService;
    private AiModelRouter modelRouter;
    private ScriptGenerationAgent scriptGenerationAgent;
    private CharacterAgent characterAgent;
    private CharacterResolverAgent characterResolverAgent;
    private PlotExtractionAgent plotExtractionAgent;
    private SceneAgent sceneAgent;
    private DialogueAgent dialogueAgent;
    private ActionAgent actionAgent;
    private ScriptComposer scriptComposer;
    private YamlExporter yamlExporter;

    // ── 真实层（被测核心）──
    private WorkflowEngine engine;
    private WorkflowDefinitions definitions;
    private GenerationOrchestrator orchestrator;

    /** spy 引擎捕获 execute() 返回的 future —— 拿 executionId 的唯一通道。 */
    private final AtomicReference<CompletableFuture<String>> engineFuture = new AtomicReference<>();

    /** 各步骤的起始 / 结束时间戳（agent mock 内打点），用于断言依赖顺序与并行重叠。 */
    private final Map<WorkflowStep, Long> startMarks = new ConcurrentHashMap<>();
    private final Map<WorkflowStep, Long> endMarks = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        scriptService = mock(ScriptService.class);
        novelService = mock(NovelService.class);
        modelRouter = mock(AiModelRouter.class);
        scriptGenerationAgent = mock(ScriptGenerationAgent.class);
        characterAgent = mock(CharacterAgent.class);
        characterResolverAgent = mock(CharacterResolverAgent.class);
        plotExtractionAgent = mock(PlotExtractionAgent.class);
        sceneAgent = mock(SceneAgent.class);
        dialogueAgent = mock(DialogueAgent.class);
        actionAgent = mock(ActionAgent.class);
        scriptComposer = mock(ScriptComposer.class);
        yamlExporter = mock(YamlExporter.class);

        WorkflowStateManager stateManager = new WorkflowStateManager();
        engine = spy(new WorkflowEngine(stateManager));
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            CompletableFuture<String> future = (CompletableFuture<String>) inv.callRealMethod();
            engineFuture.set(future);
            return future;
        }).when(engine).execute(any(Workflow.class));

        definitions = new WorkflowDefinitions(yamlExporter, scriptService);
        orchestrator = new GenerationOrchestrator(scriptService, novelService, modelRouter,
                /* singlePassEnabled */ false, /* singlePassStaged */ true,
                scriptGenerationAgent,
                characterAgent, characterResolverAgent, plotExtractionAgent,
                sceneAgent, dialogueAgent, actionAgent, scriptComposer,
                engine, definitions);

        Novel novel = testNovel();
        when(novelService.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        // runPipeline 打印模型信息时调用；mock 默认返回 null 会 NPE，需显式置空 Map
        when(modelRouter.availableModels()).thenReturn(Map.of());
    }

    // ═══════════════════════════════════════════════════════
    //  用例 1：全链路成功
    // ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("工作流按依赖顺序执行，DIALOGUE 与 ACTION 并行，9 步均 COMPLETED")
    void shouldExecuteFullWorkflow() throws Exception {
        stubSuccessfulAgents();
        Script script = testScript();

        String execId = launchAndWaitForEngine(script);

        // completeScript 是成功路径的最后一拍 —— 以它为"编排器收尾完成"的同步点
        verify(scriptService, timeout(WAIT_MS)).completeScript(SCRIPT_ID);

        // ── 9 步全部 COMPLETED ──
        Map<WorkflowStep, StepStatus> state = engine.getState(execId);
        for (WorkflowStep ws : WorkflowStep.values()) {
            assertEquals(StepStatus.COMPLETED, state.get(ws), ws + " 应为 COMPLETED，实际状态图: " + state);
        }
        assertEquals(100.0, engine.getProgress(execId).overallProgress(), 0.01);

        // ── 依赖顺序：CHARACTER_EXTRACT 在 CHAPTER_PARSE 之后 ──
        assertNotNull(startMarks.get(WorkflowStep.CHARACTER_EXTRACT), "CharacterAgent 应被调用");
        assertNotNull(endMarks.get(WorkflowStep.CHAPTER_PARSE), "CHAPTER_PARSE 应已执行");
        assertTrue(startMarks.get(WorkflowStep.CHARACTER_EXTRACT) > endMarks.get(WorkflowStep.CHAPTER_PARSE),
                "CHARACTER_EXTRACT 必须在 CHAPTER_PARSE 之后开始");

        // ── 依赖顺序：SCRIPT_COMPOSE 在三个并行前置之后 ──
        long composeStart = startMarks.get(WorkflowStep.SCRIPT_COMPOSE);
        assertTrue(composeStart > endMarks.get(WorkflowStep.CHARACTER_RESOLVE),
                "SCRIPT_COMPOSE 必须在 CHARACTER_RESOLVE 之后");
        assertTrue(composeStart > endMarks.get(WorkflowStep.DIALOGUE_GENERATE),
                "SCRIPT_COMPOSE 必须在 DIALOGUE_GENERATE 之后");
        assertTrue(composeStart > endMarks.get(WorkflowStep.ACTION_GENERATE),
                "SCRIPT_COMPOSE 必须在 ACTION_GENERATE 之后");

        // ── 并行证据：DIALOGUE 与 ACTION 的执行窗口必须重叠 ──
        assertTrue(startMarks.get(WorkflowStep.DIALOGUE_GENERATE) < endMarks.get(WorkflowStep.ACTION_GENERATE)
                        && startMarks.get(WorkflowStep.ACTION_GENERATE) < endMarks.get(WorkflowStep.DIALOGUE_GENERATE),
                "DIALOGUE 与 ACTION 必须并行执行（执行窗口重叠）");

        // ── 落库：引擎产出（内存 ctx）经编排器写入 ScriptService ──
        verify(scriptService).updateCharacters(eq(SCRIPT_ID),
                argThat(list -> list != null && !list.isEmpty()));
        verify(scriptService).updateScenes(eq(SCRIPT_ID),
                argThat(list -> list != null && !list.isEmpty()));
        verify(scriptService, never()).markFailed(SCRIPT_ID);
    }

    // ═══════════════════════════════════════════════════════
    //  用例 2：步骤失败 → 下游 SKIPPED，整体 FAILED
    // ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("某步骤失败时下游 SKIP 且整体标记失败")
    void shouldFailGracefully() throws Exception {
        stubSuccessfulAgents();
        // 覆盖场景切分：AI 服务抛异常 → 重试耗尽 → SCENE_SEGMENT=FAILED
        when(sceneAgent.segment(anyList(), anyList(), anyList()))
                .thenThrow(new RuntimeException("AI 场景切分服务不可用"));
        Script script = testScript();

        String execId = launchAndWaitForEngine(script);

        // markFailed 是失败路径的最后一拍
        verify(scriptService, timeout(WAIT_MS)).markFailed(SCRIPT_ID);

        Map<WorkflowStep, StepStatus> state = engine.getState(execId);

        // 失败点本身
        assertEquals(StepStatus.FAILED, state.get(WorkflowStep.SCENE_SEGMENT),
                "重试耗尽的步骤必须保持 FAILED");

        // 传递依赖方显式 SKIPPED（W16 失败传播）
        assertEquals(StepStatus.SKIPPED, state.get(WorkflowStep.DIALOGUE_GENERATE));
        assertEquals(StepStatus.SKIPPED, state.get(WorkflowStep.ACTION_GENERATE));
        assertEquals(StepStatus.SKIPPED, state.get(WorkflowStep.SCRIPT_COMPOSE));
        assertEquals(StepStatus.SKIPPED, state.get(WorkflowStep.YAML_EXPORT));

        // 上游分支不受影响
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.CHAPTER_PARSE));
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.CHARACTER_EXTRACT));
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.CHARACTER_RESOLVE));
        assertEquals(StepStatus.COMPLETED, state.get(WorkflowStep.PLOT_EXTRACT));

        // 下游 Agent 一个都不许被调（不编造数据）
        verifyNoInteractions(dialogueAgent, actionAgent, scriptComposer, yamlExporter);

        // 整体终态 FAILED（不谎报成功），不落任何成功终态
        WorkflowProgress progress = engine.getProgress(execId);
        assertTrue(progress.message().toLowerCase().contains("failed"),
                "整体消息必须报告失败，实际: " + progress.message());
        verify(scriptService, never()).completeScript(SCRIPT_ID);
        verify(scriptService, never()).completeWithWarnings(eq(SCRIPT_ID), eq(0), eq(0));
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    /**
     * 发起生成并等待引擎工作流跑完，返回 executionId。
     * {@code launchGeneration} 无返回句柄，通过 spy 捕获的 future 等待。
     */
    private String launchAndWaitForEngine(Script script) throws Exception {
        orchestrator.launchGeneration(script);

        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (engineFuture.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertNotNull(engineFuture.get(),
                "编排器应在 " + WAIT_MS + "ms 内调用 engine.execute()");
        return engineFuture.get().get(WAIT_MS, TimeUnit.MILLISECONDS);
    }

    private Script testScript() {
        return Script.builder()
                .id(SCRIPT_ID)
                .novelId(NOVEL_ID)
                .build();
    }

    private Novel testNovel() {
        Chapter chapter = Chapter.builder()
                .chapterNumber(1)
                .title("第一章 试炼")
                .content("林动盘膝而坐，体内元力缓缓流动。他睁开眼，眸中掠过一丝精光。")
                .charCount(100)
                .build();
        Novel novel = Novel.builder()
                .id(NOVEL_ID)
                .title("测试小说")
                .build();
        novel.setChapters(chaptersWithParseMark(List.of(chapter)));
        return novel;
    }

    /**
     * CHAPTER_PARSE 步骤没有独立 Agent 可打点 —— 借助列表子类：
     * {@code chapterParse} 会调用 {@code size()}（打日志），在该调用处记录时间戳。
     * （runPipeline 的前置校验只调 {@code isEmpty()}，不会误触发。）
     */
    private List<Chapter> chaptersWithParseMark(List<Chapter> chapters) {
        return new ArrayList<>(chapters) {
            @Override
            public int size() {
                mark(WorkflowStep.CHAPTER_PARSE);
                return super.size();
            }
        };
    }

    /** 打点：起始时间只记第一次，结束时间总是覆盖。 */
    private void mark(WorkflowStep step) {
        long now = System.nanoTime();
        startMarks.putIfAbsent(step, now);
        endMarks.put(step, now);
    }

    /** 为 9 步工作流中的 7 个 Agent 驱动步骤打成功桩（CHAPTER_PARSE/YAML_EXPORT 不走 Agent）。 */
    private void stubSuccessfulAgents() {
        when(characterAgent.extract(anyList(), anyList())).thenAnswer(inv -> {
            mark(WorkflowStep.CHARACTER_EXTRACT);
            return List.of(new CharacterExtractionResult(
                    "林动", List.of("林家小子"), CharacterRoleType.PROTAGONIST,
                    "男", "16-18", "坚毅的天才少年",
                    List.of("坚毅", "不服输"), List.of(), "第一章", 5));
        });

        when(characterResolverAgent.resolve(anyList())).thenAnswer(inv -> {
            mark(WorkflowStep.CHARACTER_RESOLVE);
            return List.of(Character.builder()
                    .canonicalName("林动")
                    .roleType(CharacterRoleType.PROTAGONIST)
                    .build());
        });

        when(plotExtractionAgent.extract(anyList(), anyList())).thenAnswer(inv -> {
            mark(WorkflowStep.PLOT_EXTRACT);
            return List.of();
        });

        when(sceneAgent.segment(anyList(), anyList(), anyList())).thenAnswer(inv -> {
            mark(WorkflowStep.SCENE_SEGMENT);
            return List.of(
                    Scene.builder().title("洞府修炼").summary("林动在洞府中修炼。").build(),
                    Scene.builder().title("离家远行").summary("林动背起行囊离开。").build());
        });

        // 拉长两个并行分支的执行窗口（150ms），保证重叠可被可靠观测
        when(dialogueAgent.generate(any(), anyList(), anyList(), any(), any(), any()))
                .thenAnswer(inv -> {
                    startMarks.putIfAbsent(WorkflowStep.DIALOGUE_GENERATE, System.nanoTime());
                    Thread.sleep(150);
                    endMarks.put(WorkflowStep.DIALOGUE_GENERATE, System.nanoTime());
                    Scene scene = inv.getArgument(0);
                    return List.of(Dialogue.builder()
                            .id(1L).sceneId(scene.getId()).characterId(1L)
                            .speaker("林动").content("测试台词")
                            .emotion(Emotion.CALM).sequence(1)
                            .createdAt(LocalDateTime.now()).build());
                });

        when(actionAgent.generate(any(), anyList(), anyList())).thenAnswer(inv -> {
            startMarks.putIfAbsent(WorkflowStep.ACTION_GENERATE, System.nanoTime());
            Thread.sleep(150);
            endMarks.put(WorkflowStep.ACTION_GENERATE, System.nanoTime());
            Scene scene = inv.getArgument(0);
            return List.of(Action.builder()
                    .id(1L).sceneId(scene.getId())
                    .description("测试动作").sequence(1).build());
        });

        when(scriptComposer.compose(any())).thenAnswer(inv -> {
            mark(WorkflowStep.SCRIPT_COMPOSE);
            return Script.builder().id(SCRIPT_ID).title("集成测试剧本").build();
        });

        when(yamlExporter.exportToString(any(Script.class))).thenAnswer(inv -> {
            mark(WorkflowStep.YAML_EXPORT);
            return "script:\n  title: 集成测试剧本";
        });
    }
}
