package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.model.ActionDialogueSequence;
import com.novel2script.application.service.agent.model.CompositionInput;
import com.novel2script.application.service.agent.validator.CompositionValidator;
import com.novel2script.common.enums.*;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.PlotEvent;
import com.novel2script.domain.model.Scene;
import com.novel2script.domain.model.Script;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

// Note: com.novel2script.domain.model.Character is used via fully-qualified name
// to avoid ambiguity with java.lang.Character

/**
 * Unit tests for {@link ScriptComposer} — script assembly,
 * character linking, scene ordering, and validation.
 */
@DisplayName("ScriptComposer — 剧本组装测试")
class ScriptComposerTest {

    private ScriptComposer composer;
    private CompositionValidator validator;
    private List<com.novel2script.domain.model.Character> characters;
    private List<Scene> scenes;
    private List<Dialogue> dialogues;
    private List<Action> actions;
    private List<PlotEvent> plotEvents;
    private List<Chapter> chapters;

    @BeforeEach
    void setUp() {
        composer = new ScriptComposer(new ActionAgent(null, null, null));
        validator = new CompositionValidator();

        // Build test data
        characters = List.<com.novel2script.domain.model.Character>of(
                character(1L, "林川", CharacterRoleType.PROTAGONIST,
                        List.of("川哥", "小川"), "大学生穿越者"),
                character(2L, "李雪", CharacterRoleType.SUPPORTING,
                        List.of("雪儿"), "女主角"),
                character(3L, "王磊", CharacterRoleType.SUPPORTING,
                        List.of(), "林川的好友"),
                character(4L, "幽冥老人", CharacterRoleType.ANTAGONIST,
                        List.of("幽冥"), "反派BOSS"),
                character(5L, "张大壮", CharacterRoleType.MINOR,
                        List.of("大壮"), "同学")
        );

        chapters = List.of(
                chapter(1L, 1, "穿越醒来"),
                chapter(2L, 2, "初识世界"),
                chapter(3L, 3, "街头遭遇")
        );

        scenes = List.of(
                scene(1L, 1, "教室初醒", "教室", TimeOfDay.MORNING,
                        true, "林川在教室醒来", List.of(1L)),
                scene(2L, 2, "街头遭遇", "街道", TimeOfDay.NIGHT,
                        false, "林川遇到黑衣人", List.of(3L)),
                scene(3L, 3, "好友对话", "咖啡厅", TimeOfDay.AFTERNOON,
                        true, "林川与王磊商议", List.of(2L))
        );

        dialogues = List.of(
                dialogue(1L, 1L, 1, "林川", "这里是哪里？"),
                dialogue(2L, 1L, 2, "张大壮", "川哥，你可算醒了！"),
                dialogue(3L, 2L, 1, "林川", "你是谁？"),
                dialogue(4L, 2L, 2, "李雪", "有人让我把这个给你。"),
                dialogue(5L, 3L, 1, "林川", "王磊，我需要你的帮助。"),
                dialogue(6L, 3L, 2, "王磊", "你说吧，兄弟。")
        );

        actions = List.of(
                action(1L, 1L, 1, "林川睁开眼睛环顾四周"),
                action(2L, 1L, 2, "张大壮从旁边座位上站起来"),
                action(3L, 2L, 1, "林川握紧拳头"),
                action(4L, 2L, 2, "黑衣人从怀中取出信封"),
                action(5L, 3L, 1, "林川将咖啡杯放在桌上"),
                action(6L, 3L, 2, "王磊身体前倾")
        );

        plotEvents = List.of(
                PlotEvent.builder()
                        .id(1L).eventOrder(1).title("穿越")
                        .description("林川穿越到异世界").conflictType(ConflictType.PERSON_VS_FATE)
                        .chapterIds(List.of(1L)).importance(5).build()
        );
    }

    // ──────────────────────────────────────────────────
    //  1. Character ID mapping
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("1. 角色名 → ID 映射")
    class CharacterIdMapping {

        @Test
        @DisplayName("正名和别名都应有映射")
        void shouldMapCanonicalNamesAndAliases() {
            Map<String, Long> idMap = composer.buildCharacterIdMap(characters);

            assertThat(idMap).hasSize(10); // 5 canonical + 5 aliases
            assertThat(idMap.get("林川")).isEqualTo(1L);
            assertThat(idMap.get("川哥")).isEqualTo(1L);
            assertThat(idMap.get("小川")).isEqualTo(1L);
            assertThat(idMap.get("雪儿")).isEqualTo(2L);
            assertThat(idMap.get("幽冥")).isEqualTo(4L);
            assertThat(idMap.get("大壮")).isEqualTo(5L);
        }

        @Test
        @DisplayName("空角色列表应返回空映射")
        void shouldReturnEmptyForNullCharacters() {
            Map<String, Long> idMap = composer.buildCharacterIdMap(null);
            assertThat(idMap).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────
    //  2. Dialogue linking
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("2. 对白 → 角色关联")
    class DialogueLinking {

        @Test
        @DisplayName("speaker 名应对应到正确的 characterId")
        void shouldLinkSpeakersToCharacterIds() {
            Map<String, Long> idMap = composer.buildCharacterIdMap(characters);
            List<Dialogue> linked = composer.linkDialoguesToCharacters(dialogues, idMap);

            // 林川's dialogues
            assertThat(linked.get(0).getCharacterId()).isEqualTo(1L);
            assertThat(linked.get(0).getSpeaker()).isEqualTo("林川");

            // 张大壮
            assertThat(linked.get(1).getCharacterId()).isEqualTo(5L);
            assertThat(linked.get(1).getSpeaker()).isEqualTo("张大壮");

            // 王磊
            assertThat(linked.get(5).getCharacterId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("别名 speaker 应被正确匹配")
        void shouldMatchAliasSpeakers() {
            Map<String, Long> idMap = Map.of(
                    "林川", 1L,
                    "川哥", 1L
            );
            List<Dialogue> testDialogues = List.of(
                    dialogue(1L, 1L, 1, "川哥", "你好")
            );

            List<Dialogue> linked = composer.linkDialoguesToCharacters(testDialogues, idMap);

            assertThat(linked.get(0).getCharacterId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("无法匹配的 speaker 应保留对白但 characterId 为 null")
        void shouldPreserveUnresolvedDialogues() {
            Map<String, Long> idMap = composer.buildCharacterIdMap(characters);
            List<Dialogue> testDialogues = List.of(
                    dialogue(1L, 1L, 1, "黑衣人", "..." ) // not in character list
            );

            List<Dialogue> linked = composer.linkDialoguesToCharacters(testDialogues, idMap);

            assertThat(linked).hasSize(1);
            assertThat(linked.get(0).getSpeaker()).isEqualTo("黑衣人");
            assertThat(linked.get(0).getCharacterId()).isNull();
        }
    }

    // ──────────────────────────────────────────────────
    //  3. Full composition
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("3. 完整组装")
    class FullComposition {

        @Test
        @DisplayName("5角色 + 3场景 + 6对白 + 6动作 → 完整 Script")
        void shouldComposeFullScript() {
            CompositionInput input = new CompositionInput(
                    100L, "星辰变", characters, scenes,
                    dialogues, actions, plotEvents, chapters
            );

            Script script = composer.compose(input);

            assertNotNull(script);
            assertEquals("星辰变", script.getTitle());
            assertEquals(100L, script.getNovelId());
            assertEquals(1, script.getVersion());
            assertEquals(5, script.getCharacterCount());
            assertEquals(3, script.getSceneCount());
            assertEquals(6, script.getDialogueCount());
            assertEquals(ScriptStatus.COMPLETED, script.getStatus());
            assertEquals(100.0, script.getProgress());

            // Characters should be preserved
            assertThat(script.getCharacters()).hasSize(5);

            // Scenes should be sorted by scene number
            List<Scene> resultScenes = script.getScenes();
            assertThat(resultScenes).hasSize(3);
            assertThat(resultScenes.get(0).getSceneNumber()).isEqualTo(1);
            assertThat(resultScenes.get(1).getSceneNumber()).isEqualTo(2);
            assertThat(resultScenes.get(2).getSceneNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("场景应按 sceneNumber 排序")
        void shouldSortScenesByNumber() {
            // Create unsorted scenes
            List<Scene> unsorted = new ArrayList<>(List.of(
                    scene(3L, 30, "场景三", "地点C", TimeOfDay.NIGHT,
                            true, "第三个场景", List.of(1L)),
                    scene(1L, 10, "场景一", "地点A", TimeOfDay.MORNING,
                            true, "第一个场景", List.of(1L)),
                    scene(2L, 20, "场景二", "地点B", TimeOfDay.AFTERNOON,
                            true, "第二个场景", List.of(1L))
            ));

            CompositionInput input = new CompositionInput(
                    100L, "测试", characters, unsorted,
                    List.of(), List.of(), List.of(), chapters
            );

            Script script = composer.compose(input);

            assertThat(script.getScenes()).hasSize(3);
            // Should be re-numbered 1,2,3
            assertThat(script.getScenes().get(0).getSceneNumber()).isEqualTo(1);
            assertThat(script.getScenes().get(1).getSceneNumber()).isEqualTo(2);
            assertThat(script.getScenes().get(2).getSceneNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("场景内应有动作-对白交错序列")
        void shouldHaveActionDialogueSequences() {
            CompositionInput input = new CompositionInput(
                    100L, "测试", characters, scenes,
                    dialogues, actions, plotEvents, chapters
            );

            Script script = composer.compose(input);

            // workflowState should contain scene sequences
            @SuppressWarnings("unchecked")
            Map<Long, List<ActionDialogueSequence>> sequences =
                    (Map<Long, List<ActionDialogueSequence>>)
                            script.getWorkflowState().get("sceneSequences");

            assertNotNull(sequences);
            assertThat(sequences).containsKey(1L);
            assertThat(sequences).containsKey(2L);

            // Scene 1 should have 2 actions + 2 dialogues = 4 items
            List<ActionDialogueSequence> scene1Timeline = sequences.get(1L);
            assertThat(scene1Timeline).hasSize(4);

            // Should interleave correctly
            long actionCount1 = scene1Timeline.stream()
                    .filter(i -> i instanceof ActionDialogueSequence.ActionItem).count();
            long dialogueCount1 = scene1Timeline.stream()
                    .filter(i -> i instanceof ActionDialogueSequence.DialogueItem).count();
            assertThat(actionCount1).isEqualTo(2);
            assertThat(dialogueCount1).isEqualTo(2);
        }

        @Test
        @DisplayName("无效输入应抛出异常")
        void shouldThrowForInvalidInput() {
            CompositionInput invalid = new CompositionInput(
                    null, "", null, null, null, null, null, null
            );

            assertThrows(IllegalArgumentException.class, () -> composer.compose(invalid));
        }
    }

    // ──────────────────────────────────────────────────
    //  4. Validation
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("4. 关联校验")
    class CompositionValidation {

        @Test
        @DisplayName("完整正确的 Script 应通过所有校验")
        void shouldPassValidationForCorrectScript() {
            CompositionInput input = new CompositionInput(
                    100L, "测试", characters, scenes,
                    dialogues, actions, plotEvents, chapters
            );

            Script script = composer.compose(input);
            List<String> issues = validator.validate(script);

            assertThat(issues)
                    .as("A correctly composed script should have no issues")
                    .isEmpty();
            assertTrue(validator.isValid(script));
        }

        @Test
        @DisplayName("缺少角色的场景应被检测")
        void shouldDetectMissingCharacters() {
            Script script = Script.builder()
                    .title("测试")
                    .scenes(List.of(
                            Scene.builder()
                                    .id(1L).sceneNumber(1).title("无角色场景")
                                    .characterIds(Collections.emptyList())
                                    .chapterIds(List.of(1L))
                                    .build()
                    ))
                    .characters(new ArrayList<>())
                    .build();

            List<String> issues = validator.validate(script);
            assertThat(issues).isNotEmpty();
            assertThat(issues.stream().anyMatch(i -> i.contains("no characters"))).isTrue();
        }

        @Test
        @DisplayName("序号不连续应被检测")
        void shouldDetectNonSequentialNumbering() {
            Script script = Script.builder()
                    .title("测试")
                    .scenes(List.of(
                            Scene.builder()
                                    .id(1L).sceneNumber(1).title("场景1")
                                    .characterIds(List.of(1L))
                                    .chapterIds(List.of(1L))
                                    .build(),
                            Scene.builder()
                                    .id(3L).sceneNumber(5).title("场景5")  // jump
                                    .characterIds(List.of(1L))
                                    .chapterIds(List.of(1L))
                                    .build()
                    ))
                    .characters(List.<com.novel2script.domain.model.Character>of(character(1L, "测试", CharacterRoleType.PROTAGONIST,
                            List.of(), "")))
                    .build();

            List<String> issues = validator.validate(script);
            assertThat(issues.stream().anyMatch(i -> i.contains("Missing scene number"))).isTrue();
        }

        @Test
        @DisplayName("孤立的对白（无 sceneId）应被检测")
        void shouldDetectOrphanedDialogues() {
            Script script = Script.builder()
                    .title("测试")
                    .scenes(List.of(
                            Scene.builder()
                                    .id(1L).sceneNumber(1).title("场景")
                                    .characterIds(List.of(1L))
                                    .chapterIds(List.of(1L))
                                    .dialogues(List.of(
                                            Dialogue.builder()
                                                    .speaker("林川").content("你好")
                                                    .sceneId(null) // orphaned
                                                    .build()
                                    ))
                                    .build()
                    ))
                    .characters(List.<com.novel2script.domain.model.Character>of(character(1L, "林川", CharacterRoleType.PROTAGONIST,
                            List.of(), "")))
                    .build();

            List<String> issues = validator.validate(script);
            assertThat(issues.stream().anyMatch(i -> i.contains("Orphaned dialogue"))).isTrue();
        }

        @Test
        @DisplayName("缺失章节映射的场景应被检测")
        void shouldDetectMissingChapterMapping() {
            Script script = Script.builder()
                    .title("测试")
                    .scenes(List.of(
                            Scene.builder()
                                    .id(1L).sceneNumber(1).title("无章节映射")
                                    .characterIds(List.of(1L))
                                    .chapterIds(Collections.emptyList()) // no chapters
                                    .build()
                    ))
                    .characters(List.<com.novel2script.domain.model.Character>of(character(1L, "测试", CharacterRoleType.PROTAGONIST,
                            List.of(), "")))
                    .build();

            List<String> issues = validator.validate(script);
            assertThat(issues.stream().anyMatch(i -> i.contains("no chapter source"))).isTrue();
        }

        @Test
        @DisplayName("null Script 应返回错误")
        void shouldReportNullScript() {
            List<String> issues = validator.validate(null);
            assertThat(issues).contains("Script is null");
        }
    }

    // ──────────────────────────────────────────────────
    //  5. Edge cases
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("5. 边缘情况")
    class EdgeCases {

        @Test
        @DisplayName("空对话/空动作应安全处理")
        void shouldHandleEmptyDialoguesAndActions() {
            CompositionInput input = new CompositionInput(
                    100L, "测试", characters,
                    List.of(scene(1L, 1, "单人场景", "房间", TimeOfDay.MORNING,
                            true, "只有环境", List.of(1L))),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), chapters
            );

            Script script = composer.compose(input);

            assertNotNull(script);
            assertThat(script.getDialogueCount()).isEqualTo(0);
            assertThat(script.getScenes()).hasSize(1);
        }

        @Test
        @DisplayName("buildCharacterIdMap 应处理 null 别名")
        void shouldHandleNullAliases() {
            com.novel2script.domain.model.Character withNullAlias = com.novel2script.domain.model.Character.builder()
                    .id(10L).canonicalName("测试").aliases(null).build();

            Map<String, Long> map = composer.buildCharacterIdMap(
                    List.<com.novel2script.domain.model.Character>of(withNullAlias));
            assertThat(map).hasSize(1);
            assertThat(map.get("测试")).isEqualTo(10L);
        }

        @Test
        @DisplayName("场景内对白应按 sequence 排序")
        void shouldSortDialoguesBySequence() {
            List<Dialogue> unsorted = List.of(
                    dialogue(1L, 1L, 3, "王磊", "第三句"),
                    dialogue(2L, 1L, 1, "林川", "第一句"),
                    dialogue(3L, 1L, 2, "李雪", "第二句")
            );

            CompositionInput input = new CompositionInput(
                    100L, "测试", characters,
                    List.of(scene(1L, 1, "场景", "地点", TimeOfDay.MORNING,
                            true, "测试", List.of(1L))),
                    unsorted, List.of(), List.of(), chapters
            );

            Script script = composer.compose(input);
            List<Dialogue> sorted = script.getScenes().get(0).getDialogues();

            assertThat(sorted).hasSize(3);
            assertThat(sorted.get(0).getSequence()).isEqualTo(1);
            assertThat(sorted.get(1).getSequence()).isEqualTo(2);
            assertThat(sorted.get(2).getSequence()).isEqualTo(3);
        }
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    private static com.novel2script.domain.model.Character character(
            Long id, String name, CharacterRoleType role,
            List<String> aliases, String description) {
        return com.novel2script.domain.model.Character.builder()
                .id(id).canonicalName(name).roleType(role)
                .aliases(aliases).description(description)
                .build();
    }

    private static Chapter chapter(Long id, int num, String title) {
        return Chapter.builder()
                .id(id).chapterNumber(num).title(title)
                .content("测试内容...").build();
    }

    private static Scene scene(Long id, int num, String title, String location,
                                TimeOfDay tod, boolean interior, String summary,
                                List<Long> chapterIds) {
        return Scene.builder()
                .id(id).sceneNumber(num).title(title).location(location)
                .timeOfDay(tod).interior(interior).summary(summary)
                .chapterIds(chapterIds).characterIds(new ArrayList<>())
                .dialogues(new ArrayList<>()).actions(new ArrayList<>())
                .build();
    }

    private static Dialogue dialogue(Long id, Long sceneId, int seq,
                                      String speaker, String content) {
        return Dialogue.builder()
                .id(id).sceneId(sceneId).sequence(seq)
                .speaker(speaker).content(content)
                .emotion(Emotion.NEUTRAL).build();
    }

    private static Action action(Long id, Long sceneId, int seq, String description) {
        return Action.builder()
                .id(id).sceneId(sceneId).sequence(seq)
                .description(description)
                .actionType(ActionType.classify(description).name())
                .durationMs(1500).build();
    }
}
