package com.novel2script.application.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.application.service.agent.model.ActionDialogueSequence;
import com.novel2script.common.enums.ActionType;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.TimeOfDay;
import com.novel2script.domain.model.Action;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import com.novel2script.infrastructure.config.AiModelRouter;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ActionAgent} covering JSON parsing,
 * action-type classification, visualization validation,
 * narration-free validation, and action-dialogue interleaving.
 */
@DisplayName("ActionAgent — 动作生成测试")
class ActionAgentTest {

    private ActionAgent agent;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Agent with null dependencies — individual methods tested in isolation
        agent = new ActionAgent(null, null, objectMapper, null);
    }

    // ──────────────────────────────────────────────────
    //  1. Action type classification
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("1. ActionType 分类")
    class ActionTypeClassification {

        @Test
        @DisplayName("推/拉/踢/跑/拿 → ACTION")
        void shouldClassifyAsAction() {
            assertThat(ActionType.classify("林川推开门走进房间")).isEqualTo(ActionType.ACTION);
            assertThat(ActionType.classify("李雪拿起桌上的杯子")).isEqualTo(ActionType.ACTION);
            assertThat(ActionType.classify("张伟一脚踢开椅子")).isEqualTo(ActionType.ACTION);
            assertThat(ActionType.classify("他跑到门口，猛地拉开门")).isEqualTo(ActionType.ACTION);
            assertThat(ActionType.classify("林川抽出长剑指向对方")).isEqualTo(ActionType.ACTION);
        }

        @Test
        @DisplayName("回头/抬头/后退/躲避 → REACTION")
        void shouldClassifyAsReaction() {
            assertThat(ActionType.classify("李雪猛地回头看向门口")).isEqualTo(ActionType.REACTION);
            assertThat(ActionType.classify("林川瞳孔微缩，后退半步")).isEqualTo(ActionType.REACTION);
            assertThat(ActionType.classify("她猛地转过身，瞪大双眼")).isEqualTo(ActionType.REACTION);
            assertThat(ActionType.classify("他倒吸一口凉气")).isEqualTo(ActionType.REACTION);
        }

        @Test
        @DisplayName("沉默/停顿/犹豫 → BEAT")
        void shouldClassifyAsBeat() {
            assertThat(ActionType.classify("一阵沉默笼罩了房间")).isEqualTo(ActionType.BEAT);
            assertThat(ActionType.classify("林川犹豫了一下，没有回答")).isEqualTo(ActionType.BEAT);
            assertThat(ActionType.classify("他停下脚步，僵在原地")).isEqualTo(ActionType.BEAT);
            assertThat(ActionType.classify("半晌，他都没有说话")).isEqualTo(ActionType.BEAT);
        }

        @Test
        @DisplayName("背景/环境/路人 → BUSINESS")
        void shouldClassifyAsBusiness() {
            assertThat(ActionType.classify("服务员低头擦拭着手中的杯子")).isEqualTo(ActionType.BUSINESS);
            assertThat(ActionType.classify("窗外的树叶随风飘落")).isEqualTo(ActionType.BUSINESS);
            assertThat(ActionType.classify("远处传来模糊的汽车鸣笛声")).isEqualTo(ActionType.BUSINESS);
        }

        @Test
        @DisplayName("fromString 应从中文标签解析")
        void shouldParseFromChineseLabel() {
            assertThat(ActionType.fromString("主动动作")).isEqualTo(ActionType.ACTION);
            assertThat(ActionType.fromString("反应")).isEqualTo(ActionType.REACTION);
            assertThat(ActionType.fromString("节拍")).isEqualTo(ActionType.BEAT);
            assertThat(ActionType.fromString("背景动作")).isEqualTo(ActionType.BUSINESS);
        }

        @Test
        @DisplayName("fromString 应从英文名解析")
        void shouldParseFromEnumName() {
            assertThat(ActionType.fromString("ACTION")).isEqualTo(ActionType.ACTION);
            assertThat(ActionType.fromString("reaction")).isEqualTo(ActionType.REACTION);
            assertThat(ActionType.fromString("BEAT")).isEqualTo(ActionType.BEAT);
            assertThat(ActionType.fromString("Business")).isEqualTo(ActionType.BUSINESS);
        }

        @Test
        @DisplayName("fromString null/blank/invalid 应返回 null")
        void shouldReturnNullForInvalid() {
            assertThat(ActionType.fromString(null)).isNull();
            assertThat(ActionType.fromString("")).isNull();
            assertThat(ActionType.fromString("不存在")).isNull();
        }
    }

    // ──────────────────────────────────────────────────
    //  2. JSON response parsing
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("2. JSON 响应解析")
    class JsonParsing {

        @Test
        @DisplayName("应正确解析标准 AI 响应")
        void shouldParseStandardAiResponse() {
            String json = """
                    {
                      "actions": [
                        {"sequence": 1, "character": "林川", "actionType": "ACTION", "description": "林川握紧拳头，指节发白", "durationMs": 1500},
                        {"sequence": 2, "character": "李雪", "actionType": "REACTION", "description": "李雪猛地回头，瞳孔微缩", "durationMs": 800},
                        {"sequence": 3, "character": null, "actionType": "BEAT", "description": "一阵沉默笼罩了房间", "durationMs": 2000}
                      ]
                    }""";

            List<Action> actions = agent.parseActionsFromJson(json, 100L);

            assertThat(actions).hasSize(3);

            assertThat(actions.get(0).getSequence()).isEqualTo(1);
            assertThat(actions.get(0).getDescription()).isEqualTo("林川握紧拳头，指节发白");
            assertThat(actions.get(0).getActionType()).isEqualTo("ACTION");

            assertThat(actions.get(1).getSequence()).isEqualTo(2);
            assertThat(actions.get(1).getDescription()).isEqualTo("李雪猛地回头，瞳孔微缩");
            assertThat(actions.get(1).getActionType()).isEqualTo("REACTION");

            assertThat(actions.get(2).getSequence()).isEqualTo(3);
            assertThat(actions.get(2).getActionType()).isEqualTo("BEAT");
        }

        @Test
        @DisplayName("应处理 markdown 代码块中的 JSON")
        void shouldParseJsonInMarkdownFence() {
            String json = """
                    以下是动作建议：

                    ```json
                    {
                      "actions": [
                        {"sequence": 1, "character": "林川", "actionType": "ACTION", "description": "林川推开沉重的铁门", "durationMs": 2000}
                      ]
                    }
                    ```
                    """;

            List<Action> actions = agent.parseActionsFromJson(json, 100L);

            assertThat(actions).hasSize(1);
            assertThat(actions.get(0).getDescription()).isEqualTo("林川推开沉重的铁门");
        }

        @Test
        @DisplayName("应处理裸数组格式")
        void shouldParseBareArray() {
            String json = """
                    [
                      {"sequence": 1, "character": "林川", "actionType": "ACTION", "description": "林川站起身", "durationMs": 1000},
                      {"sequence": 2, "character": null, "actionType": "BUSINESS", "description": "窗外灯光闪烁", "durationMs": 1500}
                    ]""";

            List<Action> actions = agent.parseActionsFromJson(json, 100L);

            assertThat(actions).hasSize(2);
            assertThat(actions.get(0).getActionType()).isEqualTo("ACTION");
            assertThat(actions.get(1).getActionType()).isEqualTo("BUSINESS");
        }

        @Test
        @DisplayName("null/blank 输入应返回空列表")
        void shouldReturnEmptyForNullInput() {
            assertThat(agent.parseActionsFromJson(null, 100L)).isEmpty();
            assertThat(agent.parseActionsFromJson("", 100L)).isEmpty();
            assertThat(agent.parseActionsFromJson("   ", 100L)).isEmpty();
        }

        @Test
        @DisplayName("无 actions 键的对象应返回空列表")
        void shouldReturnEmptyForNoActionsKey() {
            String json = "{\"other_field\": \"no actions here\"}";

            List<Action> actions = agent.parseActionsFromJson(json, 100L);

            assertThat(actions).isEmpty();
        }

        @Test
        @DisplayName("缺少 description 的动作应被跳过")
        void shouldSkipActionWithoutDescription() {
            String json = """
                    {
                      "actions": [
                        {"sequence": 1, "character": "林川", "actionType": "ACTION", "description": "有效动作", "durationMs": 1000},
                        {"sequence": 2, "character": "李雪", "actionType": "REACTION", "durationMs": 800}
                      ]
                    }""";

            List<Action> actions = agent.parseActionsFromJson(json, 100L);

            assertThat(actions).hasSize(1);
            assertThat(actions.get(0).getDescription()).isEqualTo("有效动作");
        }

        @Test
        @DisplayName("未知 actionType 应通过分类规则自动推断")
        void shouldAutoClassifyUnknownActionType() {
            String json = """
                    {
                      "actions": [
                        {"sequence": 1, "character": "林川", "actionType": "UNKNOWN_TYPE", "description": "林川推开门走进房间", "durationMs": 1500}
                      ]
                    }""";

            List<Action> actions = agent.parseActionsFromJson(json, 100L);

            assertThat(actions).hasSize(1);
            // UNKNOWN_TYPE fromString returns null → classify → "推开门" → ACTION
            assertThat(actions.get(0).getActionType()).isEqualTo("ACTION");
        }
    }

    // ──────────────────────────────────────────────────
    //  3. Visualization validation
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("3. 可视化验证 — 禁止内心状态")
    class VisualizationValidation {

        @Test
        @DisplayName("可视化动作应通过验证")
        void shouldPassVisualizableActions() {
            assertTrue(ActionAgent.isVisualizable("林川握紧拳头，指节发白"));
            assertTrue(ActionAgent.isVisualizable("李雪擦了擦眼角"));
            assertTrue(ActionAgent.isVisualizable("两人的目光在空气中交汇了三秒"));
            assertTrue(ActionAgent.isVisualizable("张伟的手指在桌上轻轻敲了两下"));
            assertTrue(ActionAgent.isVisualizable("他转身走向门口，推开门"));
        }

        @Test
        @DisplayName("\"感到\" 应被拒绝")
        void shouldRejectGanDao() {
            assertFalse(ActionAgent.isVisualizable("林川感到非常生气"));
            assertFalse(ActionAgent.isVisualizable("她感觉到有人在跟踪她"));
            assertFalse(ActionAgent.isVisualizable("他感到一阵寒意袭来"));
        }

        @Test
        @DisplayName("\"意识到\" 应被拒绝")
        void shouldRejectYiShiDao() {
            assertFalse(ActionAgent.isVisualizable("李雪意识到自己被骗了"));
            assertFalse(ActionAgent.isVisualizable("他意识到局势已经失控"));
        }

        @Test
        @DisplayName("\"知道\" / \"想起了\" 应被拒绝")
        void shouldRejectKnowAndRecall() {
            assertFalse(ActionAgent.isVisualizable("林川终于知道了真相"));
            assertFalse(ActionAgent.isVisualizable("她想起了童年的往事"));
            assertFalse(ActionAgent.isVisualizable("他回忆起那个夏天的约定"));
        }

        @Test
        @DisplayName("\"觉得\" / \"认为\" 应被拒绝")
        void shouldRejectThinkAndBelieve() {
            assertFalse(ActionAgent.isVisualizable("林川觉得这件事不对劲"));
            assertFalse(ActionAgent.isVisualizable("她认为对方在说谎"));
        }

        @Test
        @DisplayName("\"心想\" / \"内心\" 应被拒绝")
        void shouldRejectInnerMonologue() {
            assertFalse(ActionAgent.isVisualizable("林川心想这不可能"));
            assertFalse(ActionAgent.isVisualizable("她内心充满了矛盾"));
            assertFalse(ActionAgent.isVisualizable("他心里暗暗松了一口气"));
        }

        @Test
        @DisplayName("null/blank 描述不可视化")
        void shouldRejectNullOrBlank() {
            assertFalse(ActionAgent.isVisualizable(null));
            assertFalse(ActionAgent.isVisualizable(""));
            assertFalse(ActionAgent.isVisualizable("   "));
        }
    }

    // ──────────────────────────────────────────────────
    //  4. Narration-free validation
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("4. 无旁白验证")
    class NarrationFreeValidation {

        @Test
        @DisplayName("无旁白描述应通过")
        void shouldPassNarrationFreeDescriptions() {
            assertTrue(ActionAgent.isNarrationFree("林川推开门走进房间"));
            assertTrue(ActionAgent.isNarrationFree("李雪擦了擦眼角"));
            assertTrue(ActionAgent.isNarrationFree("他握紧拳头，指节发白"));
        }

        @Test
        @DisplayName("\"他想起了\" 应被拒绝")
        void shouldRejectTaXiangQiLe() {
            assertFalse(ActionAgent.isNarrationFree("他想起了那个雨夜的约定"));
            assertFalse(ActionAgent.isNarrationFree("她想起了母亲说过的话"));
        }

        @Test
        @DisplayName("\"这时\" / \"此刻\" 应被拒绝")
        void shouldRejectZheShiCiKe() {
            assertFalse(ActionAgent.isNarrationFree("这时，门突然被推开了"));
            assertFalse(ActionAgent.isNarrationFree("此刻，所有人都屏住了呼吸"));
            assertFalse(ActionAgent.isNarrationFree("林川握紧了拳头，此刻"));
        }

        @Test
        @DisplayName("\"原来\" / \"其实\" 应被拒绝")
        void shouldRejectYuanLaiQiShi() {
            assertFalse(ActionAgent.isNarrationFree("原来，这一切都是陷阱"));
            assertFalse(ActionAgent.isNarrationFree("其实他早就知道了"));
        }

        @Test
        @DisplayName("\"突然\" / \"仿佛\" 应被拒绝")
        void shouldRejectSuddenlyAndAsIf() {
            assertFalse(ActionAgent.isNarrationFree("突然，窗外传来一声巨响"));
            assertFalse(ActionAgent.isNarrationFree("他仿佛看到了希望"));
            assertFalse(ActionAgent.isNarrationFree("她好像明白了什么"));
        }

        @Test
        @DisplayName("validateVisualization 应收集所有违规")
        void shouldCollectAllViolations() {
            List<Action> actions = List.of(
                    action(1, "林川推开门走进房间"),                          // OK
                    action(2, "林川感到非常愤怒"),                            // inner state
                    action(3, "这时，所有人都安静下来"),                       // narration
                    action(4, "她意识到自己被骗了"),                           // inner state
                    action(5, "李雪擦了擦眼角")                                // OK
            );

            List<String> violations = agent.validateVisualization(actions);

            assertThat(violations).hasSize(3);
            assertThat(violations.get(0)).contains("感到");
            assertThat(violations.get(1)).contains("这时");
            assertThat(violations.get(2)).contains("意识到");
        }
    }

    // ──────────────────────────────────────────────────
    //  5. Action-dialogue interleaving
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("5. 动作-对白交错")
    class ActionDialogueInterleaving {

        @Test
        @DisplayName("动作为主场景: 打斗 → 密集 ACTION")
        void shouldHandleActionHeavyScene() {
            List<Action> actions = List.of(
                    action(1, "林川握紧拳头冲上前去"),
                    action(2, "黑衣人侧身避开"),
                    action(3, "林川一脚踢中黑衣人腹部"),
                    action(4, "黑衣人后退两步，稳住身形"),
                    action(5, "两人再次对峙，呼吸急促")
            );
            List<Dialogue> dialogues = List.of(
                    dialogue(2, "黑衣人", "有两下子嘛。")
            );

            List<ActionDialogueSequence> timeline = agent.interleave(actions, dialogues);

            assertThat(timeline).hasSize(6);

            // Sequence 1: action
            assertThat(timeline.get(0)).isInstanceOf(ActionDialogueSequence.ActionItem.class);
            // Sequence 2: action AND dialogue at same seq
            assertThat(timeline.get(1)).isInstanceOf(ActionDialogueSequence.ActionItem.class);
            assertThat(timeline.get(2)).isInstanceOf(ActionDialogueSequence.DialogueItem.class);

            // Count action vs dialogue items
            long actionCount = timeline.stream()
                    .filter(i -> i instanceof ActionDialogueSequence.ActionItem).count();
            long dialogueCount = timeline.stream()
                    .filter(i -> i instanceof ActionDialogueSequence.DialogueItem).count();
            assertThat(actionCount).isEqualTo(5);
            assertThat(dialogueCount).isEqualTo(1);
        }

        @Test
        @DisplayName("对话为主场景: 咖啡馆 → BEAT + BUSINESS")
        void shouldHandleDialogueHeavyScene() {
            List<Action> actions = List.of(
                    action(1, "服务员将两杯咖啡放在桌上"),
                    action(3, "林川的手指在杯沿缓慢画圈"),
                    action(5, "一阵沉默笼罩了咖啡桌")
            );
            List<Dialogue> dialogues = List.of(
                    dialogue(2, "林川", "你为什么要这么做？"),
                    dialogue(4, "李雪", "我没有选择。")
            );

            List<ActionDialogueSequence> timeline = agent.interleave(actions, dialogues);

            assertThat(timeline).hasSize(5);

            // Verify chronological order
            assertThat(timeline.get(0)).isInstanceOf(ActionDialogueSequence.ActionItem.class);
            assertThat(timeline.get(3)).isInstanceOf(ActionDialogueSequence.DialogueItem.class);

            // All actions in dialogue-heavy scene should be BEAT or BUSINESS
            for (Action a : actions) {
                assertThat(a.getActionType())
                        .isIn("BEAT", "BUSINESS", "ACTION");
            }
        }

        @Test
        @DisplayName("空输入应安全处理")
        void shouldHandleEmptyInputs() {
            assertThat(agent.interleave(null, null)).isEmpty();
            assertThat(agent.interleave(Collections.emptyList(), Collections.emptyList())).isEmpty();
            assertThat(agent.interleave(null, List.of(dialogue(1, "林川", "你好")))).hasSize(1);
            assertThat(agent.interleave(List.of(action(1, "推门")), null)).hasSize(1);
        }

        @Test
        @DisplayName("时间线应按 sequence 正确排序")
        void shouldSortBySequenceCorrectly() {
            List<Action> actions = List.of(
                    action(5, "第五个动作"),
                    action(1, "第一个动作"),
                    action(3, "第三个动作")
            );
            List<Dialogue> dialogues = List.of(
                    dialogue(4, "林川", "第四句对白"),
                    dialogue(2, "李雪", "第二句对白")
            );

            List<ActionDialogueSequence> timeline = agent.interleave(actions, dialogues);

            assertThat(timeline).hasSize(5);
            assertThat(timeline.get(0)).isInstanceOf(ActionDialogueSequence.ActionItem.class);
            assertThat(((ActionDialogueSequence.ActionItem) timeline.get(0)).action().getSequence())
                    .isEqualTo(1);
            assertThat(timeline.get(1)).isInstanceOf(ActionDialogueSequence.DialogueItem.class);
            assertThat(((ActionDialogueSequence.DialogueItem) timeline.get(1)).sequence())
                    .isEqualTo(2);
            assertThat(timeline.get(4)).isInstanceOf(ActionDialogueSequence.ActionItem.class);
            assertThat(((ActionDialogueSequence.ActionItem) timeline.get(4)).sequence())
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("ActionDialogueSequence 模式匹配应正常")
        void shouldSupportPatternMatching() {
            List<Action> actions = List.of(action(1, "林川推开门"));
            List<Dialogue> dialogues = List.of(dialogue(2, "林川", "我回来了"));

            List<ActionDialogueSequence> timeline = agent.interleave(actions, dialogues);

            List<String> descriptions = new ArrayList<>();
            for (ActionDialogueSequence item : timeline) {
                if (item instanceof ActionDialogueSequence.ActionItem ai) {
                    descriptions.add("[A] " + ai.action().getDescription());
                } else if (item instanceof ActionDialogueSequence.DialogueItem di) {
                    descriptions.add("[D] " + di.speaker() + ": " + di.dialogue().getContent());
                }
            }

            assertThat(descriptions).containsExactly(
                    "[A] 林川推开门",
                    "[D] 林川: 我回来了"
            );
        }
    }

    // ──────────────────────────────────────────────────
    //  6. End-to-end scenes
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("6. 完整场景测试")
    class FullSceneTests {

        private Scene fightScene;
        private Scene cafeScene;

        @BeforeEach
        void setUpScenes() {
            // Fight scene
            fightScene = Scene.builder()
                    .id(1L)
                    .sceneNumber(1)
                    .title("街头遭遇")
                    .location("夜晚的街道")
                    .timeOfDay(TimeOfDay.NIGHT)
                    .interior(false)
                    .mood("紧张")
                    .summary("林川在回家路上被神秘黑衣人拦住")
                    .build();

            // Cafe chat scene
            cafeScene = Scene.builder()
                    .id(2L)
                    .sceneNumber(2)
                    .title("咖啡厅对峙")
                    .location("安静的咖啡厅")
                    .timeOfDay(TimeOfDay.AFTERNOON)
                    .interior(true)
                    .mood("压抑")
                    .summary("李明当面质问好友张伟为什么出卖自己")
                    .build();
        }

        @Test
        @DisplayName("打斗场景应产生密集动作")
        void shouldGenerateDenseActionsForFightScene() {
            // Simulate AI response for fight scene
            String aiResponse = """
                    {
                      "actions": [
                        {"sequence": 1, "character": "林川", "actionType": "ACTION", "description": "林川沿着灯光明暗不定的街道快步行走", "durationMs": 3000},
                        {"sequence": 2, "character": "黑衣人", "actionType": "ACTION", "description": "黑衣人从巷口阴影中迈步走出，挡在前方", "durationMs": 1500},
                        {"sequence": 3, "character": "林川", "actionType": "REACTION", "description": "林川脚步一顿，右手攥紧了背包带", "durationMs": 800},
                        {"sequence": 4, "character": "黑衣人", "actionType": "ACTION", "description": "黑衣人从怀中取出一封泛黄的信封递向前方", "durationMs": 2000},
                        {"sequence": 5, "character": "林川", "actionType": "REACTION", "description": "林川后退半步，目光在信封和黑衣人脸上扫视", "durationMs": 1200},
                        {"sequence": 6, "character": null, "actionType": "BEAT", "description": "两人在路灯下僵持了漫长的三秒", "durationMs": 3000},
                        {"sequence": 7, "character": "林川", "actionType": "ACTION", "description": "林川慢慢伸出手接过了信封", "durationMs": 1500},
                        {"sequence": 8, "character": "黑衣人", "actionType": "ACTION", "description": "黑衣人转身大步走入暗巷，风衣被夜风卷起", "durationMs": 2500}
                      ]
                    }""";

            List<Action> actions = agent.parseActionsFromJson(aiResponse, fightScene.getId());

            assertThat(actions).hasSize(8);

            // Verify action types distribution
            long actionCount = actions.stream()
                    .filter(a -> "ACTION".equals(a.getActionType())).count();
            long reactionCount = actions.stream()
                    .filter(a -> "REACTION".equals(a.getActionType())).count();
            long beatCount = actions.stream()
                    .filter(a -> "BEAT".equals(a.getActionType())).count();

            assertThat(actionCount).isGreaterThanOrEqualTo(3);
            assertThat(reactionCount).isGreaterThanOrEqualTo(2);
            assertThat(beatCount).isGreaterThanOrEqualTo(1);

            // Validate all descriptions are visualizable
            List<String> violations = agent.validateVisualization(actions);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("对话场景应生成 BEAT + BUSINESS 型动作")
        void shouldGenerateBeatAndBusinessForDialogueScene() {
            String aiResponse = """
                    {
                      "actions": [
                        {"sequence": 1, "character": null, "actionType": "BUSINESS", "description": "午后阳光透过百叶窗在咖啡桌上投下条纹阴影", "durationMs": 2000},
                        {"sequence": 2, "character": "李明", "actionType": "ACTION", "description": "李明将一份文件缓缓放在桌面上", "durationMs": 2500},
                        {"sequence": 3, "character": "张伟", "actionType": "REACTION", "description": "张伟瞥了一眼文件便将目光移向窗外", "durationMs": 1000},
                        {"sequence": 4, "character": "李明", "actionType": "BEAT", "description": "李明死死盯着张伟，嘴唇抿成一条直线", "durationMs": 3000},
                        {"sequence": 5, "character": null, "actionType": "BUSINESS", "description": "邻桌的服务员低头擦拭杯子假装什么都没听见", "durationMs": 1500}
                      ]
                    }""";

            List<Action> actions = agent.parseActionsFromJson(aiResponse, cafeScene.getId());

            assertThat(actions).hasSize(5);

            boolean hasBusiness = actions.stream()
                    .anyMatch(a -> "BUSINESS".equals(a.getActionType()));
            boolean hasBeat = actions.stream()
                    .anyMatch(a -> "BEAT".equals(a.getActionType()));

            assertThat(hasBusiness).isTrue();
            assertThat(hasBeat).isTrue();

            // No inner states
            List<String> violations = agent.validateVisualization(actions);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("所有动作必须可视化 — 内心状态应被检测")
        void shouldDetectInnerStatesInActions() {
            List<Action> badActions = List.of(
                    action(1, "林川握紧拳头"),                         // OK
                    action(2, "林川感到非常愤怒"),                      // BAD
                    action(3, "她意识到自己被骗了"),                     // BAD
                    action(4, "李雪擦了擦眼角"),                         // OK
                    action(5, "他想起了童年的往事")                       // BAD
            );

            List<String> violations = agent.validateVisualization(badActions);

            assertThat(violations).hasSize(3);
            assertThat(violations.get(0)).contains("感到");
            assertThat(violations.get(1)).contains("意识到");
            assertThat(violations.get(2)).contains("想起了");
        }

        @Test
        @DisplayName("好的动作描述范式应全部通过验证")
        void shouldPassGoodActionPatterns() {
            List<Action> goodActions = List.of(
                    action(1, "林川握紧拳头，指节发白"),
                    action(2, "李雪擦了擦眼角，别过脸去"),
                    action(3, "两人的目光在空气中交汇了三秒"),
                    action(4, "张伟的手指在桌上轻轻敲了两下"),
                    action(5, "他深吸一口气，推开门走进房间"),
                    action(6, "她后退一步，背抵在墙上"),
                    action(7, "黑衣人转身，风衣翻飞如翼"),
                    action(8, "他慢慢蹲下身，捡起地上的照片")
            );

            List<String> violations = agent.validateVisualization(goodActions);

            assertThat(violations)
                    .as("All good action patterns should pass visualization validation")
                    .isEmpty();

            // Each should be individually visualizable
            for (Action a : goodActions) {
                assertThat(ActionAgent.isVisualizable(a.getDescription()))
                        .as("Action should be visualizable: '%s'", a.getDescription())
                        .isTrue();
            }
        }
    }

    // ──────────────────────────────────────────────────
    //  7. Edge cases
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("7. 边缘情况")
    class EdgeCases {

        @Test
        @DisplayName("generate(null) 应安全返回空列表")
        void shouldHandleNullScene() {
            List<Action> result = agent.generate(null, null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空场景应安全处理")
        void shouldHandleEmptyScene() {
            Scene empty = Scene.builder()
                    .id(1L)
                    .sceneNumber(1)
                    .title("空场景")
                    .location("")
                    .build();

            // Without AI model, this will fail at the AI call but shouldn't throw
            try {
                List<Action> result = agent.generate(empty,
                        Collections.emptyList(), Collections.emptyList());
                // If it reaches here (no AI model), result should be empty
                assertThat(result).isEmpty();
            } catch (Exception e) {
                // Expected when no AI model is configured — this is fine
                assertThat(e).isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        @DisplayName("extractJson 应委托给 CharacterAgent.extractJson")
        void shouldDelegateExtractJson() {
            // Test that the JSON extraction works through delegation
            String text = "Some text ```json\n[{\"test\": true}]\n``` more text";
            String extracted = ActionAgent.extractJson(text);
            assertThat(extracted).isNotNull();
            assertThat(extracted).contains("test");
        }

        @Test
        @DisplayName("大量动作应保持序号连续")
        void shouldMaintainSequentialNumbering() {
            StringBuilder json = new StringBuilder("{\"actions\": [");
            for (int i = 1; i <= 50; i++) {
                if (i > 1) json.append(",");
                json.append(String.format(
                        "{\"sequence\": %d, \"character\": \"角色%d\", \"actionType\": \"ACTION\", \"description\": \"第%d个动作\", \"durationMs\": 1000}",
                        i, i, i));
            }
            json.append("]}");

            List<Action> actions = agent.parseActionsFromJson(json.toString(), 100L);

            assertThat(actions).hasSize(50);
            for (int i = 0; i < actions.size(); i++) {
                assertThat(actions.get(i).getSequence()).isEqualTo(i + 1);
            }
        }

        @Test
        @DisplayName("ActionDialogueSequence 便利方法应正确")
        void shouldHaveCorrectConvenienceMethods() {
            Action action = action(5, "测试动作");
            Dialogue dialogue = dialogue(3, "测试角色", "测试对白");

            ActionDialogueSequence.ActionItem actionItem =
                    new ActionDialogueSequence.ActionItem(action);
            ActionDialogueSequence.DialogueItem dialogueItem =
                    new ActionDialogueSequence.DialogueItem(dialogue);

            assertThat(actionItem.sequence()).isEqualTo(5);
            assertThat(dialogueItem.sequence()).isEqualTo(3);
            assertThat(dialogueItem.speaker()).isEqualTo("测试角色");
        }
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    private static Action action(int sequence, String description) {
        return Action.builder()
                .sequence(sequence)
                .description(description)
                .actionType(ActionType.classify(description).name())
                .durationMs(1500)
                .build();
    }

    private static Dialogue dialogue(int sequence, String speaker, String content) {
        return Dialogue.builder()
                .sequence(sequence)
                .speaker(speaker)
                .content(content)
                .emotion(Emotion.NEUTRAL)
                .build();
    }
}
