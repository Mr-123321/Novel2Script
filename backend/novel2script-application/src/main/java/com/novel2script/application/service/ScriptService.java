package com.novel2script.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.common.enums.ContentSource;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.GenerationStatus;
import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.common.enums.TimeOfDay;
import com.novel2script.common.enums.WorkflowStep;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.*;
import com.novel2script.domain.model.Character;  // explicit — resolves ambiguity with java.lang.Character
import com.novel2script.infrastructure.mapper.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Application service for script generation and management.
 * Uses MyBatis-Plus for database persistence.
 *
 * <p>Generation is orchestrated by {@link GenerationOrchestrator},
 * which runs asynchronously and updates the script via the
 * {@code update*} methods on this service.
 */
@Slf4j
@Service
public class ScriptService {

    private final ScriptMapper scriptMapper;
    private final CharacterMapper characterMapper;
    private final SceneMapper sceneMapper;
    private final DialogueMapper dialogueMapper;
    private final ActionMapper actionMapper;
    private final PlotEventMapper plotEventMapper;
    private final PlotInsertionMapper plotInsertionMapper;
    private final GenerationOrchestrator orchestrator;

    public ScriptService(ScriptMapper scriptMapper,
                         CharacterMapper characterMapper,
                         SceneMapper sceneMapper,
                         DialogueMapper dialogueMapper,
                         ActionMapper actionMapper,
                         PlotEventMapper plotEventMapper,
                         PlotInsertionMapper plotInsertionMapper,
                         @Lazy GenerationOrchestrator orchestrator) {
        this.scriptMapper = scriptMapper;
        this.characterMapper = characterMapper;
        this.sceneMapper = sceneMapper;
        this.dialogueMapper = dialogueMapper;
        this.actionMapper = actionMapper;
        this.plotEventMapper = plotEventMapper;
        this.plotInsertionMapper = plotInsertionMapper;
        this.orchestrator = orchestrator;
    }

    // ─────────────────────────────────────────────────────
    //  Public API — called by controllers
    // ─────────────────────────────────────────────────────

    /** Start script generation (async). Returns immediately with the script stub. */
    @Transactional
    public Script generateScript(Long novelId, int maxScenes, String style,
                                  List<String> focusCharacters) {
        log.info("Starting script generation: novelId={}, maxScenes={}, style='{}'",
                novelId, maxScenes, style);

        if (maxScenes < 1 || maxScenes > 100) {
            throw new BusinessException("INVALID_SCENE_COUNT",
                    "maxScenes must be between 1 and 100, got: " + maxScenes);
        }

        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> workflowState = new LinkedHashMap<>();
        workflowState.put("maxScenes", maxScenes);
        workflowState.put("style", style != null ? style : "标准");
        workflowState.put("focusCharacters", focusCharacters != null ? focusCharacters : List.of());
        workflowState.put("currentStep", WorkflowStep.CHAPTER_PARSE.name());
        workflowState.put("startedAt", now.toString());

        Script script = Script.builder()
                .novelId(novelId)
                .title("Generating...")
                .version(1)
                .sceneCount(0)
                .characterCount(0)
                .dialogueCount(0)
                .scenes(new ArrayList<>())
                .characters(new ArrayList<>())
                .plotEvents(new ArrayList<>())
                .workflowState(workflowState)
                .status(ScriptStatus.GENERATING)
                .progress(0.0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        scriptMapper.insert(script);

        // Launch async pipeline
        orchestrator.launchGeneration(script);

        log.info("Script generation launched: scriptId={}, novelId={}", script.getId(), novelId);
        return script;
    }

    /** Find a script by ID, fully assembled with all child entities. */
    public Optional<Script> findById(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            log.debug("Script not found: id={}", scriptId);
            return Optional.empty();
        }
        assembleFullScript(script);
        log.debug("Found script: id={}, status={}", scriptId, script.getStatus());
        return Optional.of(script);
    }

    /**
     * Same as {@link #findById} but produces no log output.
     * Used by SSE polling loop to avoid flooding the log.
     */
    public Optional<Script> findByIdQuietly(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return Optional.empty();
        assembleFullScript(script);
        return Optional.of(script);
    }

    /** Save (upsert) a script to the database. */
    @Transactional
    public void save(Script script) {
        if (script.getId() == null) {
            scriptMapper.insert(script);
        } else {
            Script existing = scriptMapper.selectById(script.getId());
            if (existing != null) {
                scriptMapper.updateById(script);
            } else {
                scriptMapper.insert(script);
            }
        }
    }

    /** Return stored progress. */
    public double getProgress(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        return script != null ? script.getProgress() : 0.0;
    }

    public String getYaml(Long scriptId) {
        Script script = findById(scriptId).orElse(null);
        if (script == null) return null;
        if (script.getYamlContent() == null || script.getYamlContent().isBlank()) {
            script.setYamlContent(generateYaml(script));
            scriptMapper.updateById(script);
        }
        return script.getYamlContent();
    }

    /** Generate a human-readable TXT screenplay. */
    public String getTxt(Long scriptId) {
        Script script = findById(scriptId).orElse(null);
        if (script == null) return null;
        return generateTxt(script);
    }

    /** Generate a Markdown-formatted screenplay. */
    public String getMd(Long scriptId) {
        Script script = findById(scriptId).orElse(null);
        if (script == null) return null;
        return generateMd(script);
    }

    /**
     * Ensure the script is ready for export (generation must have finished).
     * {@link com.novel2script.common.enums.ScriptStatus#COMPLETED_WITH_WARNINGS}
     * counts as finished: gaps are left empty on purpose instead of being filled
     * with fabricated content.
     *
     * <p>Migrated from the removed {@code ExportService} so the export gate stays a
     * first-class, tested invariant of the export path.
     */
    public void ensureExportable(Script script) {
        if (script == null) {
            throw new BusinessException("SCRIPT_NOT_FOUND", "Script not found");
        }
        if (!script.isGenerated()) {
            throw new BusinessException("SCRIPT_NOT_COMPLETED",
                    "Script must be COMPLETED or COMPLETED_WITH_WARNINGS before export. Current: " + script.getStatus());
        }
    }

    public List<Script> listAll() {
        List<Script> scripts = scriptMapper.selectList(null);
        // Do NOT assemble full graph for list view (performance)
        return scripts;
    }

    // ─────────────────────────────────────────────────────
    //  Data writers — called by GenerationOrchestrator
    // ─────────────────────────────────────────────────────

    @Transactional
    public void updateProgress(Long scriptId, double progress, WorkflowStep step) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;
        script.setProgress(Math.min(100.0, Math.max(0.0, progress)));
        if (script.getWorkflowState() == null) {
            script.setWorkflowState(new LinkedHashMap<>());
        }
        script.getWorkflowState().put("currentStep", step.name());
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
    }

    /**
     * Merge per-step generation warnings into the script's workflowState.
     * Used to surface explicit failures (e.g. scenes whose dialogue could not
     * be generated) so the frontend can show "待补全" instead of fabricated data.
     */
    @Transactional
    public void recordGenerationWarnings(Long scriptId, Map<String, Object> warnings) {
        if (scriptId == null || warnings == null || warnings.isEmpty()) return;
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;
        if (script.getWorkflowState() == null) {
            script.setWorkflowState(new LinkedHashMap<>());
        }
        script.getWorkflowState().putAll(warnings);
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
    }

    @Transactional
    public void updateCharacters(Long scriptId, List<Character> characters) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;

        // Build old-id → new-id mapping for remapping scene references
        Map<Long, Long> oldToNewIds = new LinkedHashMap<>();

        // Delete all existing characters for this script
        characterMapper.delete(new LambdaQueryWrapper<Character>().eq(Character::getScriptId, scriptId));

        // Batch insert new characters, tracking ID changes
        LocalDateTime now = LocalDateTime.now();
        for (Character c : characters) {
            Long oldId = c.getId();
            c.setScriptId(scriptId);
            c.setId(null); // Let DB auto-generate
            c.setCreatedAt(now);
            characterMapper.insert(c);
            if (oldId != null && !oldId.equals(c.getId())) {
                oldToNewIds.put(oldId, c.getId());
            }
        }

        // Remap character IDs in existing scenes (if any were saved before characters)
        if (!oldToNewIds.isEmpty()) {
            List<Scene> existingScenes = sceneMapper.selectList(
                    new LambdaQueryWrapper<Scene>().eq(Scene::getScriptId, scriptId));
            for (Scene scene : existingScenes) {
                if (scene.getCharacterIds() != null && !scene.getCharacterIds().isEmpty()) {
                    List<Long> updated = scene.getCharacterIds().stream()
                            .map(cid -> oldToNewIds.getOrDefault(cid, cid))
                            .collect(Collectors.toList());
                    if (!updated.equals(scene.getCharacterIds())) {
                        scene.setCharacterIds(updated);
                        sceneMapper.updateById(scene);
                    }
                }
            }
            log.debug("Remapped {} character IDs in {} scenes", oldToNewIds.size(), existingScenes.size());
        }

        // Update script metadata
        script.setCharacterCount(characters.size());
        script.setUpdatedAt(now);
        scriptMapper.updateById(script);
    }

    @Transactional
    public void updateScenes(Long scriptId, List<Scene> scenes) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;

        // Delete all existing scenes for this script (DB cascade deletes dialogues/actions)
        sceneMapper.delete(new LambdaQueryWrapper<Scene>().eq(Scene::getScriptId, scriptId));

        // Batch insert scenes with their nested dialogues and actions
        LocalDateTime now = LocalDateTime.now();
        int totalDialogues = 0;
        for (Scene scene : scenes) {
            scene.setScriptId(scriptId);
            scene.setId(null); // Let DB auto-generate
            scene.setCreatedAt(now);
            // Generation-status columns default to COMPLETED; only the content
            // generators mark a scene FAILED (待补全)
            if (scene.getDialogueStatus() == null) scene.setDialogueStatus(GenerationStatus.COMPLETED);
            if (scene.getActionStatus() == null) scene.setActionStatus(GenerationStatus.COMPLETED);
            sceneMapper.insert(scene);

            // Insert nested dialogues
            if (scene.getDialogues() != null) {
                for (Dialogue d : scene.getDialogues()) {
                    d.setSceneId(scene.getId());
                    d.setId(null);
                    d.setCreatedAt(now);
                    // Anything reaching persistence without a stamp is AI output
                    if (d.getSource() == null) d.setSource(ContentSource.AI);
                    dialogueMapper.insert(d);
                    totalDialogues++;
                }
            }

            // Insert nested actions
            if (scene.getActions() != null) {
                for (Action a : scene.getActions()) {
                    a.setSceneId(scene.getId());
                    a.setId(null);
                    a.setCreatedAt(now);
                    if (a.getSource() == null) a.setSource(ContentSource.AI);
                    actionMapper.insert(a);
                }
            }
        }

        // Update script metadata
        script.setSceneCount(scenes.size());
        script.setDialogueCount(totalDialogues);
        script.setUpdatedAt(now);
        scriptMapper.updateById(script);
    }

    @Transactional
    public void updatePlotEvents(Long scriptId, List<PlotEvent> plotEvents) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;

        plotEventMapper.delete(new LambdaQueryWrapper<PlotEvent>().eq(PlotEvent::getScriptId, scriptId));

        LocalDateTime now = LocalDateTime.now();
        for (PlotEvent pe : plotEvents) {
            pe.setScriptId(scriptId);
            pe.setId(null);
            pe.setCreatedAt(now);
            plotEventMapper.insert(pe);
        }

        script.setUpdatedAt(now);
        scriptMapper.updateById(script);
    }

    @Transactional
    public void setTitle(Long scriptId, String title) {
        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setTitle(title);
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
    }

    /**
     * Mark generation as finished. The final status is derived from the
     * recorded generation warnings:
     * <ul>
     *   <li>no failures → {@link ScriptStatus#COMPLETED}</li>
     *   <li>some scenes failed dialogue/action generation →
     *       {@link ScriptStatus#COMPLETED_WITH_WARNINGS} (content is kept,
     *       gaps are flagged as 待补全 — never filled with fabricated data)</li>
     * </ul>
     */
    @Transactional
    public void completeScript(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;
        Map<String, Object> ws = script.getWorkflowState();
        int failedDialogues = ws == null ? 0 : readPositive(ws.get("failedDialogueScenes"));
        int failedActions = ws == null ? 0 : readPositive(ws.get("failedActionScenes"));
        finalizeScript(script, failedDialogues, failedActions);
    }

    /**
     * Mark generation as finished <em>with warnings</em>: some scenes' dialogue
     * and/or action generation failed, so those scenes are intentionally left
     * empty for manual completion (nothing fabricated). The script is still
     * finished — progress 100 and status {@link ScriptStatus#COMPLETED_WITH_WARNINGS} —
     * so the successfully generated content is kept and remains exportable.
     *
     * @param scriptId            target script
     * @param failedDialogueScenes number of scenes whose dialogue generation failed
     * @param failedActionScenes   number of scenes whose action generation failed
     */
    @Transactional
    public void completeWithWarnings(Long scriptId, int failedDialogueScenes, int failedActionScenes) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new BusinessException("SCRIPT_NOT_FOUND", "剧本不存在: " + scriptId);
        }
        finalizeScript(script, failedDialogueScenes, failedActionScenes);
    }

    /**
     * Shared terminal handling: set status (COMPLETED vs COMPLETED_WITH_WARNINGS),
     * persist the failed-scene counters into workflowState, regenerate the final
     * YAML and save. Warnings are only attached when there is at least one gap.
     */
    private void finalizeScript(Script script, int failedDialogueScenes, int failedActionScenes) {
        boolean withWarnings = failedDialogueScenes > 0 || failedActionScenes > 0;
        if (withWarnings) {
            script.completeWithWarnings();
            Map<String, Object> ws = script.getWorkflowState();
            if (ws == null) {
                ws = new LinkedHashMap<>();
                script.setWorkflowState(ws);
            }
            ws.put("failedDialogueScenes", failedDialogueScenes);
            ws.put("failedActionScenes", failedActionScenes);
        } else {
            script.complete();
        }
        // Generate final YAML
        Script fullScript = findById(script.getId()).orElse(script);
        script.setYamlContent(generateYaml(fullScript));
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        log.info("Script finished: id={}, status={}, failedDialogues={}, failedActions={}, title='{}', scenes={}, characters={}",
                script.getId(), script.getStatus(), failedDialogueScenes, failedActionScenes,
                script.getTitle(), script.getSceneCount(), script.getCharacterCount());
    }

    private int readPositive(Object value) {
        if (value instanceof Number n) return Math.max(0, n.intValue());
        if (value instanceof String s) {
            try {
                return Math.max(0, Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    @Transactional
    public void markFailed(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) return;
        script.setStatus(ScriptStatus.FAILED);
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        log.warn("Script marked FAILED: id={}", scriptId);
    }

    @Transactional
    public void deleteScript(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            log.warn("Script delete requested for non-existent id={}", scriptId);
            return;
        }
        // DB cascade handles all child tables
        scriptMapper.deleteById(scriptId);
        log.info("Script deleted: id={}, title='{}'", scriptId, script.getTitle());
    }

    // ─────────────────────────────────────────────────────
    //  Paragraph CRUD — actions & dialogues
    // ─────────────────────────────────────────────────────

    @Transactional
    public void addAction(Long scriptId, Long sceneId, Action action) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) throw new IllegalArgumentException("剧本不存在: id=" + scriptId);

        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getScriptId().equals(scriptId)) {
            throw new IllegalArgumentException("场景不存在: sceneId=" + sceneId);
        }

        action.setId(null);
        action.setSceneId(sceneId);
        action.setCreatedAt(LocalDateTime.now());
        // Created through the editing API — provenance is human, not AI
        action.setSource(ContentSource.MANUAL);
        actionMapper.insert(action);

        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        log.info("Action added: scriptId={}, sceneId={}, actionId={}", scriptId, sceneId, action.getId());
    }

    @Transactional
    public void updateAction(Long scriptId, Long sceneId, Long actionId, Map<String, Object> updates) {
        Action action = actionMapper.selectById(actionId);
        if (action == null || !action.getSceneId().equals(sceneId)) {
            throw new IllegalArgumentException("动作不存在: actionId=" + actionId);
        }

        if (updates.containsKey("description")) action.setDescription((String) updates.get("description"));
        if (updates.containsKey("actionType")) action.setActionType((String) updates.get("actionType"));
        if (updates.containsKey("sequence")) action.setSequence(((Number) updates.get("sequence")).intValue());
        if (updates.containsKey("characterId")) action.setCharacterId(((Number) updates.get("characterId")).longValue());
        if (updates.containsKey("durationMs")) action.setDurationMs(((Number) updates.get("durationMs")).intValue());

        // Edited by a human — mark it so experiment samples and the UI no
        // longer treat it as untouched AI output
        action.setSource(ContentSource.MANUAL);
        actionMapper.updateById(action);

        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
        log.info("Action updated: scriptId={}, sceneId={}, actionId={}", scriptId, sceneId, actionId);
    }

    @Transactional
    public void deleteAction(Long scriptId, Long sceneId, Long actionId) {
        Action action = actionMapper.selectById(actionId);
        if (action == null || !action.getSceneId().equals(sceneId)) {
            throw new IllegalArgumentException("动作不存在: actionId=" + actionId);
        }

        actionMapper.deleteById(actionId);

        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
        log.info("Action deleted: scriptId={}, sceneId={}, actionId={}", scriptId, sceneId, actionId);
    }

    @Transactional
    public void addDialogue(Long scriptId, Long sceneId, Dialogue dialogue) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) throw new IllegalArgumentException("剧本不存在: id=" + scriptId);

        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getScriptId().equals(scriptId)) {
            throw new IllegalArgumentException("场景不存在: sceneId=" + sceneId);
        }

        dialogue.setId(null);
        dialogue.setSceneId(sceneId);
        dialogue.setCreatedAt(LocalDateTime.now());
        // Created through the editing API — provenance is human, not AI
        dialogue.setSource(ContentSource.MANUAL);
        dialogueMapper.insert(dialogue);

        // Update dialogue count
        Long count = dialogueMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Dialogue>()
                        .eq(Dialogue::getSceneId, sceneId));
        script.setDialogueCount(count.intValue());
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        log.info("Dialogue added: scriptId={}, sceneId={}, dialogueId={}", scriptId, sceneId, dialogue.getId());
    }

    @Transactional
    public void updateDialogue(Long scriptId, Long sceneId, Long dialogueId, Map<String, Object> updates) {
        Dialogue dialogue = dialogueMapper.selectById(dialogueId);
        if (dialogue == null || !dialogue.getSceneId().equals(sceneId)) {
            throw new IllegalArgumentException("对白不存在: dialogueId=" + dialogueId);
        }

        if (updates.containsKey("speaker")) dialogue.setSpeaker((String) updates.get("speaker"));
        if (updates.containsKey("content")) dialogue.setContent((String) updates.get("content"));
        if (updates.containsKey("emotion")) {
            String emotionStr = (String) updates.get("emotion");
            dialogue.setEmotion(Emotion.fromLabel(emotionStr));
        }
        if (updates.containsKey("sequence")) dialogue.setSequence(((Number) updates.get("sequence")).intValue());
        if (updates.containsKey("characterId")) dialogue.setCharacterId(((Number) updates.get("characterId")).longValue());
        if (updates.containsKey("parenthetical")) dialogue.setParenthetical((String) updates.get("parenthetical"));

        // Edited by a human — provenance becomes MANUAL
        dialogue.setSource(ContentSource.MANUAL);
        dialogueMapper.updateById(dialogue);

        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
        log.info("Dialogue updated: scriptId={}, sceneId={}, dialogueId={}", scriptId, sceneId, dialogueId);
    }

    @Transactional
    public void deleteDialogue(Long scriptId, Long sceneId, Long dialogueId) {
        Dialogue dialogue = dialogueMapper.selectById(dialogueId);
        if (dialogue == null || !dialogue.getSceneId().equals(sceneId)) {
            throw new IllegalArgumentException("对白不存在: dialogueId=" + dialogueId);
        }

        dialogueMapper.deleteById(dialogueId);

        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            // Update dialogue count
            Long count = dialogueMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Dialogue>()
                            .eq(Dialogue::getSceneId, sceneId));
            script.setDialogueCount(count.intValue());
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
        log.info("Dialogue deleted: scriptId={}, sceneId={}, dialogueId={}", scriptId, sceneId, dialogueId);
    }

    @Transactional
    public void reorderSceneContent(Long scriptId, Long sceneId, List<Map<String, Object>> items) {
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scene.getScriptId().equals(scriptId)) {
            throw new IllegalArgumentException("场景不存在: sceneId=" + sceneId);
        }

        for (Map<String, Object> item : items) {
            String type = (String) item.get("type");
            Long itemId = ((Number) item.get("id")).longValue();
            int sequence = ((Number) item.get("sequence")).intValue();

            if ("action".equals(type)) {
                Action action = actionMapper.selectById(itemId);
                if (action != null && action.getSceneId().equals(sceneId)) {
                    action.setSequence(sequence);
                    actionMapper.updateById(action);
                }
            } else if ("dialogue".equals(type)) {
                Dialogue dialogue = dialogueMapper.selectById(itemId);
                if (dialogue != null && dialogue.getSceneId().equals(sceneId)) {
                    dialogue.setSequence(sequence);
                    dialogueMapper.updateById(dialogue);
                }
            }
        }

        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
        log.info("Scene content reordered: scriptId={}, sceneId={}, items={}", scriptId, sceneId, items.size());
    }

    /**
     * Partial update of a scene's descriptive fields. Only the keys present in
     * {@code updates} are applied — dialogues/actions are untouched.
     *
     * @throws IllegalArgumentException when the scene does not exist or does not
     *                                  belong to the given script
     */
    @Transactional
    public void updateScene(Long scriptId, Long sceneId, Map<String, Object> updates) {
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !scriptId.equals(scene.getScriptId())) {
            throw new IllegalArgumentException("场景不存在: sceneId=" + sceneId);
        }

        if (updates.containsKey("title")) scene.setTitle((String) updates.get("title"));
        if (updates.containsKey("summary")) scene.setSummary((String) updates.get("summary"));
        if (updates.containsKey("location")) scene.setLocation((String) updates.get("location"));
        if (updates.containsKey("mood")) scene.setMood((String) updates.get("mood"));
        if (updates.containsKey("sceneNumber") && updates.get("sceneNumber") instanceof Number n) {
            scene.setSceneNumber(n.intValue());
        }
        if (updates.containsKey("timeOfDay") && updates.get("timeOfDay") instanceof String todStr) {
            TimeOfDay tod = TimeOfDay.fromLabel(todStr);
            if (tod != null) scene.setTimeOfDay(tod);
        }
        if (updates.containsKey("interior") && updates.get("interior") instanceof Boolean interior) {
            scene.setInterior(interior);
        }

        sceneMapper.updateById(scene);

        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
        log.info("Scene updated: scriptId={}, sceneId={}, fields={}", scriptId, sceneId, updates.keySet());
    }

    /**
     * Resolve which scene a dialogue belongs to, validating it against the script.
     *
     * @return the owning scene id, or {@code null} when the dialogue does not
     *         exist or does not belong to the given script
     */
    public Long findSceneIdForDialogue(Long scriptId, Long dialogueId) {
        Dialogue dialogue = dialogueMapper.selectById(dialogueId);
        if (dialogue == null || dialogue.getSceneId() == null) return null;
        Scene scene = sceneMapper.selectById(dialogue.getSceneId());
        if (scene == null || !scriptId.equals(scene.getScriptId())) return null;
        return scene.getId();
    }

    @Transactional
    public void updateCharacter(Long scriptId, Long characterId, Map<String, Object> updates) {
        Character character = characterMapper.selectById(characterId);
        if (character == null || !character.getScriptId().equals(scriptId)) {
            throw new IllegalArgumentException("角色不存在: characterId=" + characterId);
        }

        String oldName = character.getCanonicalName();

        if (updates.containsKey("canonicalName"))
            character.setCanonicalName((String) updates.get("canonicalName"));
        if (updates.containsKey("roleType")) {
            String roleStr = (String) updates.get("roleType");
            character.setRoleType(CharacterRoleType.fromValue(roleStr));
        }
        if (updates.containsKey("gender"))
            character.setGender((String) updates.get("gender"));
        if (updates.containsKey("ageRange"))
            character.setAgeRange((String) updates.get("ageRange"));
        if (updates.containsKey("description"))
            character.setDescription((String) updates.get("description"));
        if (updates.containsKey("aliases")) {
            Object aliasesObj = updates.get("aliases");
            if (aliasesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> aliases = (List<String>) aliasesObj;
                character.setAliases(aliases);
            }
        }
        if (updates.containsKey("personality")) {
            Object personalityObj = updates.get("personality");
            if (personalityObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> personality = (List<String>) personalityObj;
                character.setPersonality(personality);
            }
        }
        if (updates.containsKey("relationships")) {
            Object relsObj = updates.get("relationships");
            if (relsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> rawRels = (List<Map<String, String>>) relsObj;
                List<Character.Relationship> relationships = new ArrayList<>();
                for (Map<String, String> r : rawRels) {
                    relationships.add(Character.Relationship.builder()
                            .target(r.get("target"))
                            .relation(r.get("relation"))
                            .build());
                }
                character.setRelationships(relationships);
            }
        }

        characterMapper.updateById(character);

        // Propagate canonical name change to dialogue speaker fields
        String newName = character.getCanonicalName();
        if (oldName != null && !oldName.equals(newName)) {
            int updatedCount = 0;
            List<Scene> scenes = sceneMapper.selectList(
                    new LambdaQueryWrapper<Scene>().eq(Scene::getScriptId, scriptId));
            for (Scene scene : scenes) {
                List<Dialogue> dialogues = dialogueMapper.selectList(
                        new LambdaQueryWrapper<Dialogue>().eq(Dialogue::getSceneId, scene.getId()));
                for (Dialogue dialogue : dialogues) {
                    if (dialogue.getCharacterId() != null
                            && dialogue.getCharacterId().equals(characterId)) {
                        dialogue.setSpeaker(newName);
                        dialogueMapper.updateById(dialogue);
                        updatedCount++;
                    } else if (oldName.equals(dialogue.getSpeaker())) {
                        dialogue.setSpeaker(newName);
                        dialogueMapper.updateById(dialogue);
                        updatedCount++;
                    }
                }
            }
            log.info("Character name changed: '{}' -> '{}', synced {} dialogue(s)",
                    oldName, newName, updatedCount);
        }

        // Invalidate YAML cache
        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setYamlContent(null);
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
        log.info("Character updated: scriptId={}, characterId={}, fields={}",
                scriptId, characterId, updates.keySet());
    }

    @Transactional
    public void updateYaml(Long scriptId, String yamlContent) {
        Script script = scriptMapper.selectById(scriptId);
        if (script != null) {
            script.setYamlContent(yamlContent);
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        }
    }

    // ─────────────────────────────────────────────────────
    //  Assembly: load full Script aggregate from DB
    // ─────────────────────────────────────────────────────

    private void assembleFullScript(Script script) {
        if (script == null) return;

        Long scriptId = script.getId();

        // Load characters (using LambdaQueryWrapper to ensure autoResultMap with type handlers)
        List<Character> characters = characterMapper.selectList(
                new LambdaQueryWrapper<Character>().eq(Character::getScriptId, scriptId));
        script.setCharacters(characters != null ? characters : new ArrayList<>());

        // Load scenes with nested dialogues and actions
        List<Scene> scenes = sceneMapper.selectList(
                new LambdaQueryWrapper<Scene>().eq(Scene::getScriptId, scriptId).orderByAsc(Scene::getSceneNumber));
        if (scenes != null) {
            for (Scene scene : scenes) {
                List<Dialogue> dialogues = dialogueMapper.selectList(
                        new LambdaQueryWrapper<Dialogue>().eq(Dialogue::getSceneId, scene.getId()).orderByAsc(Dialogue::getSequence));
                scene.setDialogues(dialogues != null ? dialogues : new ArrayList<>());

                List<Action> actions = actionMapper.selectList(
                        new LambdaQueryWrapper<Action>().eq(Action::getSceneId, scene.getId()).orderByAsc(Action::getSequence));
                scene.setActions(actions != null ? actions : new ArrayList<>());
            }
            script.setScenes(scenes);
        } else {
            script.setScenes(new ArrayList<>());
        }

        // Load plot events
        List<PlotEvent> plotEvents = plotEventMapper.selectList(
                new LambdaQueryWrapper<PlotEvent>().eq(PlotEvent::getScriptId, scriptId).orderByAsc(PlotEvent::getEventOrder));
        script.setPlotEvents(plotEvents != null ? plotEvents : new ArrayList<>());

        // Load plot insertions
        List<PlotInsertion> insertions = plotInsertionMapper.selectList(
                new LambdaQueryWrapper<PlotInsertion>().eq(PlotInsertion::getScriptId, scriptId).orderByAsc(PlotInsertion::getPosition));
        script.setPlotInsertions(insertions != null ? insertions : new ArrayList<>());
    }

    // ─────────────────────────────────────────────────────
    //  YAML generation
    // ─────────────────────────────────────────────────────

    private String generateYaml(Script script) {
        StringBuilder sb = new StringBuilder();
        sb.append("script:\n");
        sb.append("  id: ").append(script.getId()).append("\n");
        sb.append("  novelId: ").append(script.getNovelId()).append("\n");
        sb.append("  title: \"").append(esc(script.getTitle())).append("\"\n");
        sb.append("  status: \"").append(script.getStatus()).append("\"\n");
        sb.append("  version: ").append(script.getVersion()).append("\n");

        int sceneCount = script.getScenes() != null ? script.getScenes().size() : 0;
        int characterCount = script.getCharacters() != null ? script.getCharacters().size() : 0;
        int dialogueCount = script.getScenes() != null
                ? script.getScenes().stream().mapToInt(s -> s.getDialogues() != null ? s.getDialogues().size() : 0).sum()
                : 0;

        sb.append("  sceneCount: ").append(sceneCount).append("\n");
        sb.append("  characterCount: ").append(characterCount).append("\n");
        sb.append("  dialogueCount: ").append(dialogueCount).append("\n");

        // --- characters ---
        sb.append("\n  characters:\n");
        if (script.getCharacters() != null) {
            for (Character c : script.getCharacters()) {
                sb.append("    - id: ").append(validateId(c.getId(), 1000)).append("\n");
                sb.append("      canonicalName: \"").append(esc(c.getCanonicalName())).append("\"\n");
                sb.append("      roleType: \"").append(c.getRoleType() != null ? c.getRoleType().name() : "SUPPORTING").append("\"\n");
                sb.append("      gender: \"").append(c.getGender() != null ? c.getGender() : "OTHER").append("\"\n");
                if (c.getDescription() != null && !c.getDescription().isBlank())
                    sb.append("      description: \"").append(esc(c.getDescription())).append("\"\n");
                if (c.getPersonality() != null && !c.getPersonality().isEmpty()) {
                    sb.append("      personality:");
                    for (String p : c.getPersonality()) {
                        sb.append("\n        - \"").append(esc(p)).append("\"");
                    }
                    sb.append("\n");
                }
                if (c.getRelationships() != null && !c.getRelationships().isEmpty()) {
                    sb.append("      relationships:\n");
                    for (Character.Relationship r : c.getRelationships()) {
                        sb.append("        - target: \"").append(esc(r.getTarget()))
                          .append("\"\n          relation: \"").append(esc(r.getRelation()))
                          .append("\"\n");
                    }
                }
            }
        }

        // --- scenes ---
        sb.append("\n  scenes:\n");
        if (script.getScenes() != null) {
            for (Scene s : script.getScenes()) {
                sb.append("    - id: ").append(validateId(s.getId(), 1100)).append("\n");
                sb.append("      sceneNumber: ").append(s.getSceneNumber()).append("\n");
                sb.append("      location: \"").append(esc(s.getLocation())).append("\"\n");
                sb.append("      timeOfDay: \"").append(s.getTimeOfDay() != null ? s.getTimeOfDay().name() : "UNSPECIFIED").append("\"\n");
                sb.append("      interior: ").append(s.isInterior()).append("\n");
                sb.append("      title: \"").append(esc(s.getTitle() != null ? s.getTitle() : "")).append("\"\n");
                sb.append("      mood: \"").append(esc(s.getMood() != null ? s.getMood() : "")).append("\"\n");
                sb.append("      sceneHeading: \"").append(esc(s.getSceneHeader())).append("\"\n");

                if (s.getDialogues() != null && !s.getDialogues().isEmpty()) {
                    sb.append("      dialogues:\n");
                    for (Dialogue d : s.getDialogues()) {
                        sb.append("        - id: ").append(validateId(d.getId(), 1)).append("\n");
                        sb.append("          speaker: \"").append(esc(d.getSpeaker())).append("\"\n");
                        sb.append("          content: \"").append(esc(d.getContent())).append("\"\n");
                        sb.append("          emotion: \"").append(d.getEmotion() != null ? d.getEmotion().name() : "CALM").append("\"\n");
                        if (d.getParenthetical() != null && !d.getParenthetical().isBlank())
                            sb.append("          parenthetical: \"").append(esc(d.getParenthetical())).append("\"\n");
                    }
                }

                if (s.getActions() != null && !s.getActions().isEmpty()) {
                    sb.append("      actions:\n");
                    for (Action a : s.getActions()) {
                        sb.append("        - id: ").append(validateId(a.getId(), 1)).append("\n");
                        sb.append("          actionType: \"").append(esc(a.getActionType())).append("\"\n");
                        sb.append("          description: \"").append(esc(a.getDescription())).append("\"\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    private static long validateId(Long id, long fallback) {
        return id != null && id > 0 ? id : fallback;
    }

    private String generateTxt(Script script) {
        StringBuilder sb = new StringBuilder();
        sb.append("《").append(script.getTitle() != null ? script.getTitle() : "未命名剧本").append("》\n\n");

        List<Scene> scenes = script.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            sb.append("（暂无场景内容）\n");
            return sb.toString();
        }

        Map<Long, Character> charMap = new LinkedHashMap<>();
        if (script.getCharacters() != null) {
            for (Character c : script.getCharacters()) {
                charMap.put(c.getId(), c);
            }
        }

        for (Scene scene : scenes) {
            sb.append("═══════════════════════════════════════\n");
            sb.append("场景 ").append(scene.getSceneNumber());
            String heading = scene.getSceneHeader();
            if (heading != null && !heading.isBlank()) {
                sb.append("：").append(heading);
            } else {
                sb.append("：").append(scene.getLocation() != null ? scene.getLocation() : "未知地点");
                if (scene.getTimeOfDay() != null && !"UNKNOWN".equalsIgnoreCase(scene.getTimeOfDay().name())) {
                    sb.append(" - ").append(formatTimeOfDay(scene.getTimeOfDay().name()));
                }
            }
            sb.append("\n").append("═══════════════════════════════════════\n\n");

            if (scene.getCharacterIds() != null && !scene.getCharacterIds().isEmpty()) {
                sb.append("出场角色：");
                List<String> names = new ArrayList<>();
                for (Long cid : scene.getCharacterIds()) {
                    Character c = charMap.get(cid);
                    if (c != null) names.add(c.getCanonicalName());
                }
                sb.append(String.join("、", names));
                sb.append("\n\n");
            }

            if (scene.getMood() != null && !scene.getMood().isBlank()) {
                sb.append("场景氛围：").append(scene.getMood()).append("\n\n");
            }

            if (scene.getSummary() != null && !scene.getSummary().isBlank()) {
                sb.append("摘要：").append(scene.getSummary()).append("\n\n");
            }

            sb.append("───────────────────────────────────────\n\n");

            List<Object> contentItems = new ArrayList<>();
            if (scene.getDialogues() != null) contentItems.addAll(scene.getDialogues());
            if (scene.getActions() != null) contentItems.addAll(scene.getActions());
            contentItems.sort((a, b) -> {
                int sa = (a instanceof Dialogue) ? ((Dialogue) a).getSequence() : ((Action) a).getSequence();
                int sb2 = (b instanceof Dialogue) ? ((Dialogue) b).getSequence() : ((Action) b).getSequence();
                return Integer.compare(sa, sb2);
            });

            for (Object item : contentItems) {
                if (item instanceof Dialogue d) {
                    sb.append("  ").append(d.getSpeaker());
                    if (d.getEmotion() != null) {
                        sb.append("（").append(emotionLabel(d.getEmotion().name())).append("）");
                    }
                    if (d.getParenthetical() != null && !d.getParenthetical().isBlank()) {
                        sb.append("（").append(d.getParenthetical()).append("）");
                    }
                    sb.append("：\"").append(d.getContent()).append("\"\n\n");
                } else if (item instanceof Action a) {
                    sb.append("  [").append(actionTypeLabel(a.getActionType())).append("] ");
                    sb.append(a.getDescription());
                    if (a.getDurationMs() != null && a.getDurationMs() > 0) {
                        sb.append(" （约").append(String.format("%.1f", a.getDurationMs() / 1000.0)).append("秒）");
                    }
                    sb.append("\n\n");
                }
            }
            sb.append("───────────────────────────────────────\n\n");
        }

        return sb.toString();
    }

    private String generateMd(Script script) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 《").append(script.getTitle() != null ? script.getTitle() : "未命名剧本").append("》\n\n");

        List<Scene> scenes = script.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            sb.append("> （暂无场景内容）\n");
            return sb.toString();
        }

        Map<Long, Character> charMap = new LinkedHashMap<>();
        if (script.getCharacters() != null) {
            for (Character c : script.getCharacters()) {
                charMap.put(c.getId(), c);
            }
        }

        for (Scene scene : scenes) {
            sb.append("---\n\n");
            sb.append("## 场景 ").append(scene.getSceneNumber());
            String heading = scene.getSceneHeader();
            if (heading != null && !heading.isBlank()) {
                sb.append("：").append(heading);
            } else {
                sb.append("：").append(scene.getLocation() != null ? scene.getLocation() : "未知地点");
                if (scene.getTimeOfDay() != null && !"UNKNOWN".equalsIgnoreCase(scene.getTimeOfDay().name())) {
                    sb.append(" - ").append(formatTimeOfDay(scene.getTimeOfDay().name()));
                }
            }
            sb.append("\n\n");

            if (scene.getCharacterIds() != null && !scene.getCharacterIds().isEmpty()) {
                sb.append("**出场角色**：");
                List<String> names = new ArrayList<>();
                for (Long cid : scene.getCharacterIds()) {
                    Character c = charMap.get(cid);
                    if (c != null) names.add(c.getCanonicalName());
                }
                sb.append(String.join("、", names));
                sb.append("\n\n");
            }

            if (scene.getMood() != null && !scene.getMood().isBlank()) {
                sb.append("**场景氛围**：").append(scene.getMood()).append("\n\n");
            }

            if (scene.getSummary() != null && !scene.getSummary().isBlank()) {
                sb.append("> ").append(scene.getSummary()).append("\n\n");
            }

            List<Object> contentItems = new ArrayList<>();
            if (scene.getDialogues() != null) contentItems.addAll(scene.getDialogues());
            if (scene.getActions() != null) contentItems.addAll(scene.getActions());
            contentItems.sort((a, b) -> {
                int sa = (a instanceof Dialogue) ? ((Dialogue) a).getSequence() : ((Action) a).getSequence();
                int sb2 = (b instanceof Dialogue) ? ((Dialogue) b).getSequence() : ((Action) b).getSequence();
                return Integer.compare(sa, sb2);
            });

            for (Object item : contentItems) {
                if (item instanceof Dialogue d) {
                    sb.append("**").append(d.getSpeaker()).append("**");
                    if (d.getEmotion() != null) {
                        String el = emotionLabel(d.getEmotion().name());
                        if (!el.isEmpty()) sb.append("（").append(el).append("）");
                    }
                    if (d.getParenthetical() != null && !d.getParenthetical().isBlank()) {
                        sb.append("（").append(d.getParenthetical()).append("）");
                    }
                    sb.append("：\"").append(d.getContent()).append("\"\n\n");
                } else if (item instanceof Action a) {
                    sb.append("*[").append(actionTypeLabel(a.getActionType())).append("]* ");
                    sb.append(a.getDescription());
                    if (a.getDurationMs() != null && a.getDurationMs() > 0) {
                        sb.append(" （约").append(String.format("%.1f", a.getDurationMs() / 1000.0)).append("秒）");
                    }
                    sb.append("\n\n");
                }
            }
        }

        return sb.toString();
    }

    private static String formatTimeOfDay(String tod) {
        if (tod == null) return "";
        return switch (tod) {
            case "MORNING" -> "早晨";
            case "AFTERNOON" -> "下午";
            case "EVENING" -> "傍晚";
            case "NIGHT" -> "夜晚";
            case "DAWN" -> "黎明";
            case "LATE_NIGHT" -> "深夜";
            default -> tod;
        };
    }

    private static String emotionLabel(String emotion) {
        if (emotion == null) return "";
        return switch (emotion) {
            case "ANGRY" -> "愤怒";
            case "HAPPY" -> "开心";
            case "SAD" -> "悲伤";
            case "CALM" -> "平静";
            case "FEARFUL" -> "恐惧";
            case "SURPRISED" -> "惊讶";
            case "NEUTRAL" -> "";
            default -> emotion;
        };
    }

    private static String actionTypeLabel(String type) {
        if (type == null) return "动作";
        return switch (type) {
            case "ACTION" -> "动作";
            case "REACTION" -> "反应";
            case "BEAT" -> "节拍";
            case "BUSINESS" -> "调度";
            default -> type;
        };
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
