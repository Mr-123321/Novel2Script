package com.novel2script.application.service;

import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.common.enums.WorkflowStep;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import com.novel2script.domain.model.Character;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application service for script generation and management.
 * Uses in-memory storage as a development fallback.
 *
 * <p>Generation is orchestrated by {@link GenerationOrchestrator},
 * which runs asynchronously and updates the script via the
 * {@code update*} methods on this service.
 */
@Slf4j
@Service
public class ScriptService {

    private final Map<Long, Script> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    private final GenerationOrchestrator orchestrator;

    public ScriptService(@Lazy GenerationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    // ─────────────────────────────────────────────────────
    //  Public API — called by controllers
    // ─────────────────────────────────────────────────────

    /** Start script generation (async). Returns immediately with the script stub. */
    public Script generateScript(Long novelId, int maxScenes, String style,
                                  List<String> focusCharacters) {
        log.info("Starting script generation: novelId={}, maxScenes={}, style='{}'",
                novelId, maxScenes, style);

        if (maxScenes < 1 || maxScenes > 100) {
            throw new BusinessException("INVALID_SCENE_COUNT",
                    "maxScenes must be between 1 and 100, got: " + maxScenes);
        }

        Long id = idGenerator.getAndIncrement();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> workflowState = new LinkedHashMap<>();
        workflowState.put("maxScenes", maxScenes);
        workflowState.put("style", style != null ? style : "标准");
        workflowState.put("focusCharacters", focusCharacters != null ? focusCharacters : List.of());
        workflowState.put("currentStep", WorkflowStep.CHAPTER_PARSE.name());
        workflowState.put("startedAt", now.toString());

        Script script = Script.builder()
                .id(id)
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

        store.put(id, script);

        // Launch async pipeline
        orchestrator.launchGeneration(script);

        log.info("Script generation launched: scriptId={}, novelId={}", id, novelId);
        return script;
    }

    public Optional<Script> findById(Long scriptId) {
        Script script = store.get(scriptId);
        if (script != null) {
            log.debug("Found script: id={}, status={}", scriptId, script.getStatus());
            return Optional.of(script);
        }
        log.debug("Script not found: id={}", scriptId);
        return Optional.empty();
    }

    /**
     * Same as {@link #findById} but produces no log output.
     * Used by SSE polling loop to avoid flooding the log every 2 seconds.
     */
    public Optional<Script> findByIdQuietly(Long scriptId) {
        return Optional.ofNullable(store.get(scriptId));
    }

    /** Return stored progress — the orchestrator writes it. */
    public double getProgress(Long scriptId) {
        Script script = store.get(scriptId);
        return script != null ? script.getProgress() : 0.0;
    }

    public String getYaml(Long scriptId) {
        Script script = store.get(scriptId);
        if (script == null) return null;
        if (script.getYamlContent() == null || script.getYamlContent().isBlank()) {
            script.setYamlContent(generateYaml(script));
        }
        return script.getYamlContent();
    }

    public List<Script> listAll() {
        return new ArrayList<>(store.values());
    }

    // ─────────────────────────────────────────────────────
    //  Data writers — called by GenerationOrchestrator
    // ─────────────────────────────────────────────────────

    public void updateProgress(Long scriptId, double progress, WorkflowStep step) {
        Script script = store.get(scriptId);
        if (script == null) return;
        script.setProgress(Math.min(100.0, Math.max(0.0, progress)));
        script.getWorkflowState().put("currentStep", step.name());
        script.setUpdatedAt(LocalDateTime.now());
    }

    public void updateCharacters(Long scriptId, List<Character> characters) {
        Script script = store.get(scriptId);
        if (script == null) return;
        script.setCharacters(new ArrayList<>(characters));
        script.setCharacterCount(characters.size());
        script.setUpdatedAt(LocalDateTime.now());
    }

    public void updateScenes(Long scriptId, List<Scene> scenes) {
        Script script = store.get(scriptId);
        if (script == null) return;
        script.setScenes(new ArrayList<>(scenes));
        script.setSceneCount(scenes.size());
        int dialogueCount = scenes.stream().mapToInt(s -> s.getDialogues().size()).sum();
        script.setDialogueCount(dialogueCount);
        script.setUpdatedAt(LocalDateTime.now());
    }

    public void updatePlotEvents(Long scriptId, List<PlotEvent> plotEvents) {
        Script script = store.get(scriptId);
        if (script == null) return;
        script.setPlotEvents(new ArrayList<>(plotEvents));
        script.setUpdatedAt(LocalDateTime.now());
    }

    public void setTitle(Long scriptId, String title) {
        Script script = store.get(scriptId);
        if (script != null) {
            script.setTitle(title);
            script.setUpdatedAt(LocalDateTime.now());
        }
    }

    public void completeScript(Long scriptId) {
        Script script = store.get(scriptId);
        if (script == null) return;
        script.setProgress(100.0);
        script.setStatus(ScriptStatus.COMPLETED);
        script.setUpdatedAt(LocalDateTime.now());
        // Generate final YAML
        script.setYamlContent(generateYaml(script));
        log.info("Script completed: id={}, title='{}', scenes={}, characters={}",
                scriptId, script.getTitle(), script.getSceneCount(), script.getCharacterCount());
    }

    public void markFailed(Long scriptId) {
        Script script = store.get(scriptId);
        if (script == null) return;
        script.setStatus(ScriptStatus.FAILED);
        script.setUpdatedAt(LocalDateTime.now());
        log.warn("Script marked FAILED: id={}", scriptId);
    }

    public void updateYaml(Long scriptId, String yamlContent) {
        Script script = store.get(scriptId);
        if (script != null) {
            script.setYamlContent(yamlContent);
            script.setUpdatedAt(LocalDateTime.now());
        }
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

        // Count actual data lengths (not stored fields which may be stale)
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

                // dialogues
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

                // actions
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

    /** Ensure ID is non-null and reasonable for display. */
    private static long validateId(Long id, long fallback) {
        return id != null && id > 0 ? id : fallback;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
