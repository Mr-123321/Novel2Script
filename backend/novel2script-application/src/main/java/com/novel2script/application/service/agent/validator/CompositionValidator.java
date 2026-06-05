package com.novel2script.application.service.agent.validator;

import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Runs referential-integrity and logical-consistency checks on a
 * composed {@link Script} after {@code ScriptComposer} completes.
 *
 * <h3>Checks performed</h3>
 * <ol>
 *   <li>Every dialogue speaker resolves to a known character</li>
 *   <li>Every scene has at least one character present</li>
 *   <li>Scene numbers are sequential and non-duplicate</li>
 *   <li>Every scene has at least one chapter source mapped</li>
 *   <li>No orphaned dialogues (missing sceneId)</li>
 *   <li>No orphaned actions (missing sceneId)</li>
 *   <li>All referenced character IDs exist in the character list</li>
 * </ol>
 */
@Slf4j
@Component
public class CompositionValidator {

    /**
     * Validate a composed script.
     *
     * @param script the composed script to validate
     * @return list of issue descriptions; empty list means the script is valid
     */
    public List<String> validate(Script script) {
        if (script == null) {
            return List.of("Script is null");
        }

        List<String> issues = new ArrayList<>();

        // Build lookup sets
        Set<Long> characterIds = buildCharacterIdSet(script.getCharacters());
        Set<Long> sceneIds = buildSceneIdSet(script.getScenes());
        Set<Long> chapterIds = extractChapterIds(script.getScenes());

        // 1. Every dialogue speaker resolves to a known character
        if (script.getScenes() != null) {
            for (Scene scene : script.getScenes()) {
                if (scene.getDialogues() != null) {
                    for (Dialogue d : scene.getDialogues()) {
                        if (d.getCharacterId() != null
                                && !characterIds.contains(d.getCharacterId())) {
                            issues.add(String.format(
                                    "Dialogue speaker '%s' (scene %d) references unknown characterId=%d",
                                    d.getSpeaker(), scene.getSceneNumber(), d.getCharacterId()));
                        }
                        if (d.getCharacterId() == null && d.getSpeaker() != null
                                && !d.getSpeaker().isBlank()) {
                            issues.add(String.format(
                                    "Dialogue speaker '%s' (scene %d, seq %d) could not be resolved to any character",
                                    d.getSpeaker(), scene.getSceneNumber(), d.getSequence()));
                        }
                    }
                }

                // 2. Every scene has at least one character
                if (scene.getCharacterIds() == null || scene.getCharacterIds().isEmpty()) {
                    issues.add(String.format(
                            "Scene %d ('%s') has no characters assigned",
                            scene.getSceneNumber(), scene.getTitle()));
                }

                // 4. Every scene has chapter mapping
                if (scene.getChapterIds() == null || scene.getChapterIds().isEmpty()) {
                    issues.add(String.format(
                            "Scene %d ('%s') has no chapter source mapped",
                            scene.getSceneNumber(), scene.getTitle()));
                }

                // 5. No orphaned dialogues
                if (scene.getDialogues() != null) {
                    for (Dialogue d : scene.getDialogues()) {
                        if (d.getSceneId() == null) {
                            issues.add(String.format(
                                    "Orphaned dialogue: speaker='%s' seq=%d has no sceneId",
                                    d.getSpeaker(), d.getSequence()));
                        }
                    }
                }

                // 6. No orphaned actions
                if (scene.getActions() != null) {
                    for (Action a : scene.getActions()) {
                        if (a.getSceneId() == null) {
                            issues.add(String.format(
                                    "Orphaned action: seq=%d desc='%s' has no sceneId",
                                    a.getSequence(), a.getDescription()));
                        }
                    }
                }
            }
        }

        // 3. Scene numbers are sequential and non-duplicate
        if (script.getScenes() != null && !script.getScenes().isEmpty()) {
            Set<Integer> seenNumbers = new HashSet<>();
            for (Scene scene : script.getScenes()) {
                if (!seenNumbers.add(scene.getSceneNumber())) {
                    issues.add(String.format(
                            "Duplicate scene number %d: '%s'",
                            scene.getSceneNumber(), scene.getTitle()));
                }
            }

            // Check sequential (1..N)
            int maxNum = script.getScenes().stream()
                    .mapToInt(Scene::getSceneNumber)
                    .max().orElse(0);
            for (int expected = 1; expected <= maxNum; expected++) {
                final int e = expected;
                boolean found = script.getScenes().stream()
                        .anyMatch(s -> s.getSceneNumber() == e);
                if (!found) {
                    issues.add(String.format(
                            "Missing scene number %d (non-sequential numbering)", expected));
                }
            }
        }

        // 7. All referenced character IDs exist
        if (script.getScenes() != null) {
            for (Scene scene : script.getScenes()) {
                if (scene.getCharacterIds() != null) {
                    for (Long cid : scene.getCharacterIds()) {
                        if (!characterIds.contains(cid)) {
                            issues.add(String.format(
                                    "Scene %d references unknown characterId=%d",
                                    scene.getSceneNumber(), cid));
                        }
                    }
                }
            }
        }

        if (issues.isEmpty()) {
            log.info("CompositionValidator: script '{}' — all checks passed", script.getTitle());
        } else {
            log.warn("CompositionValidator: script '{}' — {} issue(s) found: {}",
                    script.getTitle(), issues.size(), issues);
        }

        return issues;
    }

    /**
     * Check whether a script passes all validation checks.
     */
    public boolean isValid(Script script) {
        return validate(script).isEmpty();
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    private Set<Long> buildCharacterIdSet(List<com.novel2script.domain.model.Character> characters) {
        Set<Long> ids = new HashSet<>();
        if (characters != null) {
            for (com.novel2script.domain.model.Character c : characters) {
                if (c.getId() != null) ids.add(c.getId());
            }
        }
        return ids;
    }

    private Set<Long> buildSceneIdSet(List<Scene> scenes) {
        Set<Long> ids = new HashSet<>();
        if (scenes != null) {
            for (Scene s : scenes) {
                if (s.getId() != null) ids.add(s.getId());
            }
        }
        return ids;
    }

    private Set<Long> extractChapterIds(List<Scene> scenes) {
        Set<Long> ids = new HashSet<>();
        if (scenes != null) {
            for (Scene s : scenes) {
                if (s.getChapterIds() != null) {
                    ids.addAll(s.getChapterIds());
                }
            }
        }
        return ids;
    }
}
