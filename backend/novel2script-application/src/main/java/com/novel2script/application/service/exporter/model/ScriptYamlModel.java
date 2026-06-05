package com.novel2script.application.service.exporter.model;

import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * YAML-serialization model for a {@link Script}.
 *
 * <p>This model decouples YAML structure from domain entities, allowing
 * the exporter to evolve independently of the domain.
 *
 * <p>Use {@link #convert(Script)} to transform a domain {@code Script} into
 * this model. The conversion is fully null-safe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptYamlModel {

    private MetaInfo meta;
    private List<CharacterModel> characters;
    private List<SceneModel> scenes;

    // ──────────────────────────────────────────────
    // Inner model classes
    // ──────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaInfo {
        private String title;
        private String originalNovel;
        private int version;
        private String generatedAt;
        private String generator;
        private int sceneCount;
        private int characterCount;
        private int dialogueCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterModel {
        private Long id;
        private String name;
        private List<String> aliases;
        private String roleType;
        private String description;
        private List<String> personality;
        private List<RelationModel> relationships;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationModel {
        private String target;
        private String relation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneModel {
        private int sceneNumber;
        private String heading;
        private String location;
        private String timeOfDay;
        private boolean isInterior;
        private String summary;
        private String mood;
        private List<String> chapterSources;
        private List<String> presentCharacters;
        private List<SequenceItem> sequence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SequenceItem {
        /** {@code DIALOGUE} or {@code ACTION}. */
        private String type;
        /** Character name or ID associated with this item. */
        private String character;
        /** Speaker name (populated only for dialogue items). */
        private String speaker;
        /** Action description (populated only for action items). */
        private String description;
        /** Emotion label (populated only for dialogue items). */
        private String emotion;
        /** Dialogue text (populated only for dialogue items). */
        private String content;
        /** Parenthetical stage direction (populated only for dialogue items). */
        private String parenthetical;
    }

    // ──────────────────────────────────────────────
    // Conversion
    // ──────────────────────────────────────────────

    /**
     * Convert a domain {@link Script} to its YAML model representation.
     *
     * <p>The conversion is entirely null-safe — a {@code null} input or any
     * null collection inside the script yields empty defaults rather than NPEs.
     *
     * @param script the domain script to convert (may be {@code null})
     * @return a populated {@code ScriptYamlModel}, or a model with empty
     *         collections if {@code script} is {@code null}
     */
    public static ScriptYamlModel convert(Script script) {
        if (script == null) {
            return ScriptYamlModel.builder()
                    .meta(MetaInfo.builder().build())
                    .characters(new ArrayList<>())
                    .scenes(new ArrayList<>())
                    .build();
        }

        int dialogueCount = 0;
        if (script.getScenes() != null) {
            for (Scene scene : script.getScenes()) {
                if (scene.getDialogues() != null) {
                    dialogueCount += scene.getDialogues().size();
                }
            }
        }

        MetaInfo meta = MetaInfo.builder()
                .title(script.getTitle())
                .originalNovel(script.getTitle())
                .version(script.getVersion())
                .generatedAt(LocalDateTime.now().toString())
                .generator("Novel2Script-YamlExporter")
                .sceneCount(script.getSceneCount())
                .characterCount(script.getCharacterCount())
                .dialogueCount(dialogueCount)
                .build();

        List<CharacterModel> characterModels = convertCharacters(script.getCharacters());
        List<SceneModel> sceneModels = convertScenes(script.getScenes());

        return ScriptYamlModel.builder()
                .meta(meta)
                .characters(characterModels)
                .scenes(sceneModels)
                .build();
    }

    private static List<CharacterModel> convertCharacters(
            List<com.novel2script.domain.model.Character> domainChars) {
        if (domainChars == null) {
            return new ArrayList<>();
        }
        return domainChars.stream()
                .filter(c -> c != null)
                .map(ScriptYamlModel::convertCharacter)
                .collect(Collectors.toList());
    }

    private static CharacterModel convertCharacter(
            com.novel2script.domain.model.Character c) {
        List<RelationModel> rels = new ArrayList<>();
        if (c.getRelationships() != null) {
            rels = c.getRelationships().stream()
                    .filter(r -> r != null)
                    .map(r -> RelationModel.builder()
                            .target(r.getTarget())
                            .relation(r.getRelation())
                            .build())
                    .collect(Collectors.toList());
        }

        return CharacterModel.builder()
                .id(c.getId())
                .name(c.getCanonicalName())
                .aliases(c.getAliases() != null ? new ArrayList<>(c.getAliases()) : new ArrayList<>())
                .roleType(c.getRoleType() != null ? c.getRoleType().name() : null)
                .description(c.getDescription())
                .personality(c.getPersonality() != null ? new ArrayList<>(c.getPersonality()) : new ArrayList<>())
                .relationships(rels)
                .build();
    }

    private static List<SceneModel> convertScenes(List<Scene> domainScenes) {
        if (domainScenes == null) {
            return new ArrayList<>();
        }
        return domainScenes.stream()
                .filter(s -> s != null)
                .map(ScriptYamlModel::convertScene)
                .collect(Collectors.toList());
    }

    private static SceneModel convertScene(Scene scene) {
        List<String> chapterSources = new ArrayList<>();
        if (scene.getChapterIds() != null) {
            chapterSources = scene.getChapterIds().stream()
                    .filter(id -> id != null)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        }

        List<String> presentCharacters = new ArrayList<>();
        if (scene.getCharacterIds() != null) {
            presentCharacters = scene.getCharacterIds().stream()
                    .filter(id -> id != null)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        }

        List<SequenceItem> sequence = buildSequence(scene);

        return SceneModel.builder()
                .sceneNumber(scene.getSceneNumber())
                .heading(scene.getSceneHeading())
                .location(scene.getLocation())
                .timeOfDay(scene.getTimeOfDay() != null ? scene.getTimeOfDay().name() : null)
                .isInterior(scene.isInterior())
                .summary(scene.getSummary())
                .mood(scene.getMood())
                .chapterSources(chapterSources)
                .presentCharacters(presentCharacters)
                .sequence(sequence)
                .build();
    }

    /**
     * Merge dialogues and actions into a single ordered sequence sorted by
     * their {@code sequence} fields (stable merge of two pre-sorted lists).
     */
    private static List<SequenceItem> buildSequence(Scene scene) {
        List<Dialogue> dialogues = scene.getDialogues() != null
                ? new ArrayList<>(scene.getDialogues()) : new ArrayList<>();
        List<Action> actions = scene.getActions() != null
                ? new ArrayList<>(scene.getActions()) : new ArrayList<>();

        dialogues.sort(Comparator.comparingInt(Dialogue::getSequence));
        actions.sort(Comparator.comparingInt(Action::getSequence));

        List<SequenceItem> result = new ArrayList<>();
        int di = 0, ai = 0;

        while (di < dialogues.size() && ai < actions.size()) {
            Dialogue d = dialogues.get(di);
            Action a = actions.get(ai);
            if (d.getSequence() <= a.getSequence()) {
                result.add(toSequenceItem(d));
                di++;
            } else {
                result.add(toSequenceItem(a));
                ai++;
            }
        }

        while (di < dialogues.size()) {
            result.add(toSequenceItem(dialogues.get(di++)));
        }
        while (ai < actions.size()) {
            result.add(toSequenceItem(actions.get(ai++)));
        }

        return result;
    }

    private static SequenceItem toSequenceItem(Dialogue d) {
        return SequenceItem.builder()
                .type("DIALOGUE")
                .character(d.getSpeaker())
                .speaker(d.getSpeaker())
                .description(null)
                .emotion(d.getEmotion() != null ? d.getEmotion().name() : null)
                .content(d.getContent())
                .parenthetical(d.getParenthetical())
                .build();
    }

    private static SequenceItem toSequenceItem(Action a) {
        String charRef = a.getCharacterId() != null ? String.valueOf(a.getCharacterId()) : null;
        return SequenceItem.builder()
                .type("ACTION")
                .character(charRef)
                .speaker(null)
                .description(a.getDescription())
                .emotion(null)
                .content(null)
                .parenthetical(null)
                .build();
    }
}
