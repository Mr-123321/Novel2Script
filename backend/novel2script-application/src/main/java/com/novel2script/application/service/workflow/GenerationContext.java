package com.novel2script.application.service.workflow;

import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.Novel;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared mutable context flowing through the generation workflow.
 * <p>
 * Solves the "Step.action is a {@link Runnable}" gap: each step's lambda
 * closure captures this context instance and reads its inputs from /
 * writes its outputs onto it — no signature change to {@code Step} needed.
 * <p>
 * <b>NOTE(接入前置，W12)</b>: 本类当前尚未被任何工作流步骤使用，
 * 是 {@code WorkflowEngine} 接入真实 Agent 的前置准备。接入方案见论文「总结与展望」。
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>{@code attributes} 是 {@link ConcurrentHashMap} —— 对白/动作并行分支
 *       可安全写入共享属性；</li>
 *   <li>{@code failedDialogueScenes} / {@code failedActionScenes} 使用
 *       {@link AtomicInteger} —— 并行递增无竞态（W10 缺陷 B 的教训）。</li>
 * </ul>
 *
 * <h3>数据可信性约定（W01/W02）</h3>
 * 失败场景计数只做<b>统计</b>；失败事实的权威记录仍由
 * {@code Script.workflowState}（如 {@code failedDialogueScenes} 键）与
 * 场景级 {@code dialogueStatus}/{@code actionStatus} 承担，本类不替代。
 */
public class GenerationContext {

    // ── 不可变输入 ──
    private final Long scriptId;
    private final Novel novel;

    // ── 各步骤的产出（顺序覆盖写入）──
    private List<Chapter> chapters = List.of();
    private List<CharacterExtractionResult> rawCharacters = List.of();
    private List<Character> characters = List.of();
    private List<PlotEvent> plotEvents = List.of();
    private List<Scene> scenes = List.of();

    // ── 失败统计（并行分支安全递增）──
    private final AtomicInteger failedDialogueScenes = new AtomicInteger(0);
    private final AtomicInteger failedActionScenes = new AtomicInteger(0);

    // ── 扩展属性（步骤间传递非结构化中间结果）──
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public GenerationContext(Long scriptId, Novel novel) {
        this.scriptId = scriptId;
        this.novel = novel;
    }

    // ------------------------------------------------------------------
    // Inputs
    // ------------------------------------------------------------------

    public Long getScriptId() {
        return scriptId;
    }

    public Novel getNovel() {
        return novel;
    }

    // ------------------------------------------------------------------
    // Pipeline stage outputs
    // ------------------------------------------------------------------

    public List<Chapter> getChapters() {
        return chapters;
    }

    public void setChapters(List<Chapter> chapters) {
        this.chapters = (chapters == null) ? List.of() : chapters;
    }

    public List<CharacterExtractionResult> getRawCharacters() {
        return rawCharacters;
    }

    public void setRawCharacters(List<CharacterExtractionResult> rawCharacters) {
        this.rawCharacters = (rawCharacters == null) ? List.of() : rawCharacters;
    }

    public List<Character> getCharacters() {
        return characters;
    }

    public void setCharacters(List<Character> characters) {
        this.characters = (characters == null) ? List.of() : characters;
    }

    public List<PlotEvent> getPlotEvents() {
        return plotEvents;
    }

    public void setPlotEvents(List<PlotEvent> plotEvents) {
        this.plotEvents = (plotEvents == null) ? List.of() : plotEvents;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    public void setScenes(List<Scene> scenes) {
        this.scenes = (scenes == null) ? List.of() : scenes;
    }

    // ------------------------------------------------------------------
    // Failure counters (thread-safe)
    // ------------------------------------------------------------------

    public void incrementFailedDialogueScenes() {
        failedDialogueScenes.incrementAndGet();
    }

    public void incrementFailedActionScenes() {
        failedActionScenes.incrementAndGet();
    }

    public int getFailedDialogueScenes() {
        return failedDialogueScenes.get();
    }

    public int getFailedActionScenes() {
        return failedActionScenes.get();
    }

    // ------------------------------------------------------------------
    // Extensible attributes (thread-safe)
    // ------------------------------------------------------------------

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }
}
