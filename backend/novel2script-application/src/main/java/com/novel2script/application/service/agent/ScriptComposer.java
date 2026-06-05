package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.model.ActionDialogueSequence;
import com.novel2script.application.service.agent.model.CompositionInput;
import com.novel2script.common.enums.ActionType;
import com.novel2script.common.enums.ScriptStatus;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Composes all agent outputs into a complete {@link Script} object.
 *
 * <h3>Composition pipeline</h3>
 * <ol>
 *   <li>Build character ID map (name → characterId) from resolved characters</li>
 *   <li>Link dialogue speakers to character IDs via name matching</li>
 *   <li>Link action performers to character IDs via name-in-description matching</li>
 *   <li>Link scenes to source chapters via chapter ID ranges</li>
 *   <li>Build scene-character many-to-many associations</li>
 *   <li>Sort scenes by scene number</li>
 *   <li>Interleave actions and dialogues within each scene</li>
 *   <li>Compute script-level statistics</li>
 * </ol>
 *
 * <p>After composition, {@link CompositionValidator} can be used to verify
 * referential integrity and logical consistency.
 */
@Slf4j
@Service
public class ScriptComposer {

    private final ActionAgent actionAgent;

    public ScriptComposer(ActionAgent actionAgent) {
        this.actionAgent = actionAgent;
    }

    /**
     * Compose all agent outputs into a complete script.
     *
     * @param input the composition input bundle
     * @return a fully assembled Script with all cross-references resolved
     */
    public Script compose(CompositionInput input) {
        if (input == null || !input.isValid()) {
            log.error("ScriptComposer: invalid composition input");
            throw new IllegalArgumentException("CompositionInput must have novelId, title, characters, and scenes");
        }

        log.info("ScriptComposer: composing script '{}' with {} characters, {} scenes, {} dialogues, {} actions",
                input.title(),
                input.characters().size(),
                input.scenes().size(),
                input.dialogues() != null ? input.dialogues().size() : 0,
                input.actions() != null ? input.actions().size() : 0);

        // Step 1: Build character name → ID map
        Map<String, Long> characterIdMap = buildCharacterIdMap(input.characters());

        // Step 2: Link dialogues to characters
        List<Dialogue> linkedDialogues = linkDialoguesToCharacters(
                input.dialogues(), characterIdMap);

        // Step 3: Link actions to characters
        List<Action> linkedActions = linkActionsToCharacters(
                input.actions(), characterIdMap, input.characters());

        // Step 4: Link scenes to chapters
        List<Scene> linkedScenes = linkScenesToChapters(
                input.scenes(), input.chapters());

        // Step 5: Build scene-character associations
        Map<Long, List<Long>> sceneCharacterMap = buildSceneCharacterMap(
                linkedScenes, input.characters(), linkedDialogues, linkedActions);

        // Apply character IDs to scenes
        for (Scene scene : linkedScenes) {
            List<Long> charIds = sceneCharacterMap.getOrDefault(scene.getId(), Collections.emptyList());
            scene.setCharacterIds(charIds);
        }

        // Step 6: Sort scenes by scene number
        linkedScenes = new ArrayList<>(linkedScenes);
        linkedScenes.sort(Comparator.comparingInt(Scene::getSceneNumber));

        // Re-number to ensure sequential order
        for (int i = 0; i < linkedScenes.size(); i++) {
            linkedScenes.get(i).setSceneNumber(i + 1);
        }

        // Step 7: Interleave actions and dialogues per scene
        Map<Long, List<ActionDialogueSequence>> sceneSequences = new LinkedHashMap<>();
        for (Scene scene : linkedScenes) {
            List<Action> sceneActions = linkedActions.stream()
                    .filter(a -> a.getSceneId() != null && a.getSceneId().equals(scene.getId()))
                    .collect(Collectors.toList());
            List<Dialogue> sceneDialogues = linkedDialogues.stream()
                    .filter(d -> d.getSceneId() != null && d.getSceneId().equals(scene.getId()))
                    .collect(Collectors.toList());

            List<ActionDialogueSequence> timeline = actionAgent.interleave(sceneActions, sceneDialogues);
            sceneSequences.put(scene.getId(), timeline);

            // Attach sorted actions and dialogues to scene
            scene.setActions(sceneActions.stream()
                    .sorted(Comparator.comparingInt(Action::getSequence))
                    .collect(Collectors.toList()));
            scene.setDialogues(sceneDialogues.stream()
                    .sorted(Comparator.comparingInt(Dialogue::getSequence))
                    .collect(Collectors.toList()));
        }

        // Step 8: Build the script
        Script script = Script.builder()
                .novelId(input.novelId())
                .title(input.title())
                .version(1)
                .sceneCount(linkedScenes.size())
                .characterCount(input.characters().size())
                .dialogueCount(linkedDialogues.size())
                .status(ScriptStatus.COMPLETED)
                .progress(100.0)
                .characters(new ArrayList<>(input.characters()))
                .scenes(linkedScenes)
                .plotEvents(input.plotEvents() != null
                        ? new ArrayList<>(input.plotEvents()) : new ArrayList<>())
                .workflowState(Map.of(
                        "sceneSequences", sceneSequences,
                        "compositionTimestamp", LocalDateTime.now().toString()
                ))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        log.info("ScriptComposer: composed script '{}' — {} scenes, {} characters, {} dialogues",
                script.getTitle(), script.getSceneCount(), script.getCharacterCount(),
                script.getDialogueCount());

        return script;
    }

    // ──────────────────────────────────────────────────
    //  Step 1: Character ID mapping
    // ──────────────────────────────────────────────────

    /**
     * Build a mapping from every known character name (canonical + aliases) to its ID.
     */
    Map<String, Long> buildCharacterIdMap(List<com.novel2script.domain.model.Character> characters) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (characters == null) return map;

        for (com.novel2script.domain.model.Character c : characters) {
            if (c.getCanonicalName() != null && !c.getCanonicalName().isBlank()) {
                map.put(c.getCanonicalName(), c.getId());
            }
            if (c.getAliases() != null) {
                for (String alias : c.getAliases()) {
                    if (alias != null && !alias.isBlank()) {
                        map.putIfAbsent(alias, c.getId());
                    }
                }
            }
        }

        log.debug("ScriptComposer: built character ID map with {} entries", map.size());
        return map;
    }

    // ──────────────────────────────────────────────────
    //  Step 2: Link dialogues to characters
    // ──────────────────────────────────────────────────

    /**
     * Resolve each dialogue's speaker name to a character ID.
     * Dialogues with unrecognized speakers have {@code characterId = null}
     * but are preserved in the output.
     */
    List<Dialogue> linkDialoguesToCharacters(List<Dialogue> dialogues,
                                               Map<String, Long> idMap) {
        if (dialogues == null || dialogues.isEmpty()) {
            return new ArrayList<>();
        }

        List<Dialogue> linked = new ArrayList<>();
        int unresolved = 0;

        for (Dialogue d : dialogues) {
            Dialogue linkedDialogue = Dialogue.builder()
                    .id(d.getId())
                    .sceneId(d.getSceneId())
                    .sequence(d.getSequence())
                    .speaker(d.getSpeaker())
                    .emotion(d.getEmotion())
                    .content(d.getContent())
                    .parenthetical(d.getParenthetical())
                    .replyTo(d.getReplyTo())
                    .createdAt(d.getCreatedAt())
                    .build();

            if (d.getSpeaker() != null && !d.getSpeaker().isBlank()) {
                Long charId = idMap.get(d.getSpeaker());
                if (charId != null) {
                    linkedDialogue.setCharacterId(charId);
                } else {
                    // Try fuzzy match: check if speaker is an alias substring
                    charId = fuzzyMatchName(d.getSpeaker(), idMap);
                    if (charId != null) {
                        linkedDialogue.setCharacterId(charId);
                    } else {
                        unresolved++;
                        log.debug("ScriptComposer: unresolved speaker '{}' in dialogue seq {}",
                                d.getSpeaker(), d.getSequence());
                    }
                }
            }

            linked.add(linkedDialogue);
        }

        if (unresolved > 0) {
            log.warn("ScriptComposer: {} dialogue(s) have unresolved speakers", unresolved);
        }
        return linked;
    }

    // ──────────────────────────────────────────────────
    //  Step 3: Link actions to characters
    // ──────────────────────────────────────────────────

    /**
     * Resolve action performers to character IDs.
     * Actions reference characters by name in their description; we match
     * against known character names and aliases.
     */
    List<Action> linkActionsToCharacters(List<Action> actions,
                                           Map<String, Long> idMap,
                                           List<com.novel2script.domain.model.Character> characters) {
        if (actions == null || actions.isEmpty()) {
            return new ArrayList<>();
        }

        List<Action> linked = new ArrayList<>();
        int unresolved = 0;

        for (Action a : actions) {
            Action linkedAction = Action.builder()
                    .id(a.getId())
                    .sceneId(a.getSceneId())
                    .characterId(a.getCharacterId()) // may already be set by ActionAgent
                    .sequence(a.getSequence())
                    .actionType(a.getActionType())
                    .description(a.getDescription())
                    .durationMs(a.getDurationMs())
                    .createdAt(a.getCreatedAt())
                    .build();

            // If characterId is not set, try to infer from description
            if (linkedAction.getCharacterId() == null && a.getDescription() != null) {
                String desc = a.getDescription();

                // Try exact name match
                for (com.novel2script.domain.model.Character c : characters) {
                    if (c.getCanonicalName() != null && desc.contains(c.getCanonicalName())) {
                        linkedAction.setCharacterId(c.getId());
                        break;
                    }
                }

                // Try alias match if still unresolved
                if (linkedAction.getCharacterId() == null) {
                    for (com.novel2script.domain.model.Character c : characters) {
                        if (c.getAliases() != null) {
                            for (String alias : c.getAliases()) {
                                if (alias != null && desc.contains(alias)) {
                                    linkedAction.setCharacterId(c.getId());
                                    break;
                                }
                            }
                        }
                        if (linkedAction.getCharacterId() != null) break;
                    }
                }

                if (linkedAction.getCharacterId() == null) {
                    unresolved++;
                }
            }

            // Classify action type if not set
            if (linkedAction.getActionType() == null || linkedAction.getActionType().isBlank()) {
                linkedAction.setActionType(ActionType.classify(a.getDescription()).name());
            }

            linked.add(linkedAction);
        }

        if (unresolved > 0) {
            log.debug("ScriptComposer: {} action(s) have unresolved performers (environmental)", unresolved);
        }
        return linked;
    }

    // ──────────────────────────────────────────────────
    //  Step 4: Link scenes to chapters
    // ──────────────────────────────────────────────────

    /**
     * Ensure each scene has valid chapter ID references.
     * Scenes that already have chapter IDs are preserved; otherwise,
     * chapter IDs are inferred from the scene's source chapter range.
     */
    List<Scene> linkScenesToChapters(List<Scene> scenes, List<Chapter> chapters) {
        if (scenes == null || scenes.isEmpty()) {
            return new ArrayList<>();
        }

        for (Scene scene : scenes) {
            // If scene already has chapter IDs, keep them
            if (scene.getChapterIds() != null && !scene.getChapterIds().isEmpty()) {
                continue;
            }

            // Infer from chapters if available
            if (chapters != null && !chapters.isEmpty()) {
                scene.setChapterIds(chapters.stream()
                        .map(Chapter::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
            }
        }

        return scenes;
    }

    // ──────────────────────────────────────────────────
    //  Step 5: Scene-character associations
    // ──────────────────────────────────────────────────

    /**
     * Build scene → character IDs mapping.
     * A character is considered "present" in a scene if:
     * <ol>
     *   <li>They speak a dialogue in this scene</li>
     *   <li>They perform an action in this scene</li>
     *   <li>Their name appears in the scene's summary/location</li>
     * </ol>
     */
    Map<Long, List<Long>> buildSceneCharacterMap(List<Scene> scenes,
                                                   List<com.novel2script.domain.model.Character> characters,
                                                   List<Dialogue> dialogues,
                                                   List<Action> actions) {
        Map<Long, List<Long>> map = new LinkedHashMap<>();

        if (scenes == null) return map;

        for (Scene scene : scenes) {
            Set<Long> charIds = new LinkedHashSet<>();

            // From dialogues
            if (dialogues != null) {
                for (Dialogue d : dialogues) {
                    if (d.getSceneId() != null && d.getSceneId().equals(scene.getId())
                            && d.getCharacterId() != null) {
                        charIds.add(d.getCharacterId());
                    }
                }
            }

            // From actions
            if (actions != null) {
                for (Action a : actions) {
                    if (a.getSceneId() != null && a.getSceneId().equals(scene.getId())
                            && a.getCharacterId() != null) {
                        charIds.add(a.getCharacterId());
                    }
                }
            }

            // From scene summary/location text matching
            if (characters != null) {
                String sceneText = (scene.getSummary() != null ? scene.getSummary() : "")
                        + " " + (scene.getLocation() != null ? scene.getLocation() : "");
                for (com.novel2script.domain.model.Character c : characters) {
                    if (c.getCanonicalName() != null && sceneText.contains(c.getCanonicalName())) {
                        charIds.add(c.getId());
                    }
                }
            }

            map.put(scene.getId(), new ArrayList<>(charIds));
        }

        return map;
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    /**
     * Fuzzy-match a speaker name against the character name map.
     * Handles partial matches and common Chinese name variations.
     */
    private Long fuzzyMatchName(String speaker, Map<String, Long> idMap) {
        if (speaker == null || speaker.isBlank()) return null;

        String normalized = speaker.trim();

        // Direct substring match
        for (Map.Entry<String, Long> entry : idMap.entrySet()) {
            if (entry.getKey().contains(normalized) || normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }
}
