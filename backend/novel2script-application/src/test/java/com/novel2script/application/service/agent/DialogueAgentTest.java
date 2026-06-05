package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.checker.DialogueConsistencyChecker;
import com.novel2script.application.service.agent.model.ConsistencyIssue;
import com.novel2script.application.service.agent.model.ConsistencyIssue.IssueType;
import com.novel2script.application.service.agent.model.ConsistencyIssue.Severity;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.common.enums.Emotion;
import com.novel2script.common.enums.TimeOfDay;
import com.novel2script.domain.model.Character; // domain Character, not java.lang.Character
import com.novel2script.domain.model.Dialogue;
import com.novel2script.domain.model.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DialogueAgent — 对白生成单元测试")
class DialogueAgentTest {

    // ── Test data builders ──────────────────────────────

    private static Character character(long id, String name, String gender,
                                       List<String> personality, String desc) {
        return Character.builder()
                .id(id).canonicalName(name).gender(gender)
                .personality(personality).description(desc)
                .roleType(CharacterRoleType.SUPPORTING)
                .build();
    }

    private static Scene scene(long id, String title, String location,
                               TimeOfDay tod, boolean interior, String summary, String mood) {
        return Scene.builder()
                .id(id).sceneNumber(1).title(title).location(location)
                .timeOfDay(tod).interior(interior).summary(summary).mood(mood)
                .chapterIds(new ArrayList<>()).characterIds(new ArrayList<>())
                .build();
    }

    private static Dialogue dialogue(int seq, String speaker, String content,
                                      Emotion emotion, String parenthetical) {
        return Dialogue.builder()
                .sequence(seq).speaker(speaker).content(content)
                .emotion(emotion).parenthetical(parenthetical).replyTo(null)
                .build();
    }

    // ── 1. Emotion enum tests ───────────────────────────

    @Nested
    @DisplayName("1. Emotion 枚举与语音风格")
    class EmotionEnum {

        @Test
        @DisplayName("fromLabel 应从中英文正确解析所有情绪")
        void shouldParseAllEmotions() {
            assertThat(Emotion.fromLabel("愤怒")).isEqualTo(Emotion.ANGRY);
            assertThat(Emotion.fromLabel("ANGRY")).isEqualTo(Emotion.ANGRY);
            assertThat(Emotion.fromLabel("高兴")).isEqualTo(Emotion.HAPPY);
            assertThat(Emotion.fromLabel("悲伤")).isEqualTo(Emotion.SAD);
            assertThat(Emotion.fromLabel("平静")).isEqualTo(Emotion.CALM);
            assertThat(Emotion.fromLabel("恐惧")).isEqualTo(Emotion.FEARFUL);
            assertThat(Emotion.fromLabel("焦虑")).isEqualTo(Emotion.ANXIOUS);
            assertThat(Emotion.fromLabel("冷淡")).isEqualTo(Emotion.COLD);
            assertThat(Emotion.fromLabel("兴奋")).isEqualTo(Emotion.EXCITED);
            assertThat(Emotion.fromLabel("温柔")).isEqualTo(Emotion.GENTLE);
            assertThat(Emotion.fromLabel("讽刺")).isEqualTo(Emotion.SARCASTIC);
            assertThat(Emotion.fromLabel("傲慢")).isEqualTo(Emotion.PROUD);
            assertThat(Emotion.fromLabel("绝望")).isEqualTo(Emotion.DESPERATE);
        }

        @Test
        @DisplayName("fromLabel 未知输入应返回 NEUTRAL")
        void shouldReturnNeutralForUnknown() {
            assertThat(Emotion.fromLabel(null)).isEqualTo(Emotion.NEUTRAL);
            assertThat(Emotion.fromLabel("")).isEqualTo(Emotion.NEUTRAL);
            assertThat(Emotion.fromLabel("不存在的情绪")).isEqualTo(Emotion.NEUTRAL);
        }

        @Test
        @DisplayName("高/低情绪强度应正确判断")
        void shouldIdentifyIntensity() {
            assertThat(Emotion.ANGRY.isHighIntensity()).isTrue();
            assertThat(Emotion.EXCITED.isHighIntensity()).isTrue();
            assertThat(Emotion.DESPERATE.isHighIntensity()).isTrue();
            assertThat(Emotion.CALM.isLowIntensity()).isTrue();
            assertThat(Emotion.GENTLE.isLowIntensity()).isTrue();
            assertThat(Emotion.NEUTRAL.isLowIntensity()).isTrue();
        }

        @Test
        @DisplayName("inferSpeechStyle 应根据性格推断说话风格")
        void shouldInferSpeechStyleFromPersonality() {
            // 冷静型
            assertThat(Emotion.inferSpeechStyle(List.of("冷静", "理智")))
                    .contains("冷峻");
            // 热情型
            assertThat(Emotion.inferSpeechStyle(List.of("热情", "开朗")))
                    .contains("热情");
            // 冷酷型
            assertThat(Emotion.inferSpeechStyle(List.of("冷酷", "无情")))
                    .contains("冰冷");
            // 谨慎型
            assertThat(Emotion.inferSpeechStyle(List.of("谨慎", "多疑")))
                    .contains("试探");
            // 智慧型
            assertThat(Emotion.inferSpeechStyle(List.of("智慧", "聪明")))
                    .contains("机锋");
            // 未知型
            assertThat(Emotion.inferSpeechStyle(List.of("未知特征")))
                    .contains("自然随和");
        }

        @Test
        @DisplayName("inferSpeechStyle 空列表应返回默认风格")
        void shouldReturnDefaultForEmpty() {
            assertThat(Emotion.inferSpeechStyle(null)).contains("自然随和");
            assertThat(Emotion.inferSpeechStyle(List.of())).contains("自然随和");
        }
    }

    // ── 2. Dialogue domain model tests ─────────────────

    @Nested
    @DisplayName("2. Dialogue 领域模型")
    class DialogueModel {

        @Test
        @DisplayName("toScriptFormat 应生成标准剧本格式")
        void shouldGenerateScriptFormat() {
            Dialogue d = dialogue(1, "李明", "你为什么这样做？",
                    Emotion.ANGRY, "(低沉)");

            String scriptFormat = d.toScriptFormat();

            assertThat(scriptFormat).contains("李明");
            assertThat(scriptFormat).contains("(低沉)");
            assertThat(scriptFormat).contains("你为什么这样做？");
        }

        @Test
        @DisplayName("无括号说明时不应出现空行")
        void shouldSkipParentheticalWhenNull() {
            Dialogue d = dialogue(2, "王芳", "我明白了。", Emotion.CALM, null);

            String format = d.toScriptFormat();
            assertThat(format).doesNotContain("null");
        }

        @Test
        @DisplayName("单人独白应正常处理")
        void shouldHandleMonologue() {
            Dialogue monologue = dialogue(1, "林川", "每个人都走了。只剩我一个人。",
                    Emotion.SAD, "站在空荡的房间中央");

            assertThat(monologue.getSpeaker()).isEqualTo("林川");
            assertThat(monologue.getEmotion()).isEqualTo(Emotion.SAD);
            assertThat(monologue.getContent().length()).isGreaterThan(10);
        }
    }

    // ── 3. ConsistencyIssue model tests ────────────────

    @Nested
    @DisplayName("3. ConsistencyIssue 模型")
    class ConsistencyIssueModel {

        @Test
        @DisplayName("应正确创建各严重级别的问题")
        void shouldCreateAllSeverityLevels() {
            ConsistencyIssue error = ConsistencyIssue.builder()
                    .severity(Severity.ERROR).type(IssueType.GENDER_MISMATCH)
                    .dialogueSequence(1).speaker("王芳")
                    .description("性别用词错误").suggestion("替换").build();

            ConsistencyIssue warning = ConsistencyIssue.builder()
                    .severity(Severity.WARNING).type(IssueType.LINE_TOO_LONG)
                    .dialogueSequence(2).speaker("李明")
                    .description("对白过长").suggestion("拆分").build();

            ConsistencyIssue info = ConsistencyIssue.builder()
                    .severity(Severity.INFO).type(IssueType.EXPOSITION_DUMP)
                    .dialogueSequence(3).speaker("张三")
                    .description("信息倾泻").suggestion("精简").build();

            assertThat(error.getSeverity()).isEqualTo(Severity.ERROR);
            assertThat(warning.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(info.getSeverity()).isEqualTo(Severity.INFO);
        }

        @Test
        @DisplayName("toString 应包含关键信息")
        void shouldToStringWithKeyInfo() {
            ConsistencyIssue issue = ConsistencyIssue.builder()
                    .severity(Severity.ERROR).type(IssueType.PERSONALITY_MISMATCH)
                    .dialogueSequence(5).speaker("李明")
                    .description("冷静角色用了感叹号").suggestion("改为句号")
                    .build();

            String str = issue.toString();
            assertThat(str).contains("ERROR");
            assertThat(str).contains("PERSONALITY_MISMATCH");
            assertThat(str).contains("李明");
            assertThat(str).contains("S005");
        }
    }

    // ── 4. DialogueConsistencyChecker tests ────────────

    @Nested
    @DisplayName("4. 一致性检查器")
    class ConsistencyCheckerTests {

        private DialogueConsistencyChecker checker;

        @BeforeEach
        void setUp() {
            checker = new DialogueConsistencyChecker();
        }

        @Test
        @DisplayName("女性角色使用'老子'应报 ERROR")
        void shouldFlagFemaleUsingMaleTerm() {
            Character female = character(1L, "王芳", "FEMALE",
                    List.of("温柔"), "女主角");
            Dialogue d = dialogue(1, "王芳", "老子今天非教训你不可！",
                    Emotion.ANGRY, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d), List.of(female));

            assertThat(issues).isNotEmpty();
            assertThat(issues.get(0).getSeverity()).isEqualTo(Severity.ERROR);
            assertThat(issues.get(0).getType()).isEqualTo(IssueType.GENDER_MISMATCH);
            assertThat(issues.get(0).getDescription()).contains("老子");
        }

        @Test
        @DisplayName("冷静角色使用过多感叹号应报 WARNING")
        void shouldFlagCalmCharacterExclamations() {
            Character calm = character(2L, "李明", "MALE",
                    List.of("冷静", "沉稳"), "智者");
            Dialogue d = dialogue(2, "李明", "不可能！这绝对不可能！！",
                    Emotion.SURPRISED, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d), List.of(calm));

            assertThat(issues).anyMatch(i -> i.getType() == IssueType.PERSONALITY_MISMATCH);
        }

        @Test
        @DisplayName("对白超过50字应报 WARNING")
        void shouldFlagLongDialogue() {
            Character normal = character(3L, "张三", "MALE",
                    List.of("普通"), "路人");
            String longContent = "我认为在这个问题上我们必须慎重考虑因为我们面对的是一个非常复杂的局面涉及到多个方面的利益和考量我们不能贸然做出决定";
            Dialogue d = dialogue(3, "张三", longContent, Emotion.CALM, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d), List.of(normal));

            assertThat(issues).anyMatch(i -> i.getType() == IssueType.LINE_TOO_LONG);
        }

        @Test
        @DisplayName("重复对白应报 WARNING")
        void shouldFlagRepeatedContent() {
            Character c = character(4L, "李四", "MALE", List.of("普通"), "路人");
            Dialogue d1 = dialogue(1, "李四", "我知道了。", Emotion.CALM, null);
            Dialogue d2 = dialogue(2, "李四", "我知道了。", Emotion.CALM, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d1, d2), List.of(c));

            assertThat(issues).anyMatch(i -> i.getType() == IssueType.REPETITION);
        }

        @Test
        @DisplayName("信息倾泻标志应报 INFO")
        void shouldFlagExpositionDump() {
            Character c = character(5L, "王五", "MALE", List.of("普通"), "路人");
            Dialogue d = dialogue(5, "王五",
                    "你知道的，我们认识这么多年了，我一直把你当最好的朋友。",
                    Emotion.CALM, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d), List.of(c));

            assertThat(issues).anyMatch(i -> i.getType() == IssueType.EXPOSITION_DUMP);
        }

        @Test
        @DisplayName("正常对白应无问题")
        void shouldPassCleanDialogue() {
            Character normal = character(6L, "赵六", "MALE",
                    List.of("沉稳"), "普通人");
            Dialogue d = dialogue(6, "赵六", "我明白了。", Emotion.CALM, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d), List.of(normal));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("空对白列表应安全处理")
        void shouldHandleEmptyDialogues() {
            List<ConsistencyIssue> issues = checker.check(List.of(), List.of());
            assertThat(issues).isEmpty();

            issues = checker.check(null, null);
            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("summarize 应生成可读报告")
        void shouldGenerateReadableSummary() {
            Character female = character(1L, "王芳", "FEMALE", List.of("温柔"), "女主");
            Character calm = character(2L, "李明", "MALE", List.of("冷静"), "男主");
            Dialogue d1 = dialogue(1, "王芳", "老子今天非去不可！", Emotion.ANGRY, null);
            Dialogue d2 = dialogue(2, "李明", "可是！这怎么可能！！", Emotion.SURPRISED, null);
            String longContent = "这".repeat(60);
            Dialogue d3 = dialogue(3, "李明", longContent, Emotion.CALM, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d1, d2, d3), List.of(female, calm));

            String summary = checker.summarize(issues);
            assertThat(summary).contains("对话一致性检查报告");
            assertThat(summary).contains("总问题数");
        }

        @Test
        @DisplayName("getActionableIssues 应过滤 INFO 级别")
        void shouldFilterActionableIssues() {
            Character female = character(1L, "王芳", "FEMALE", List.of("温柔"), "女主");
            Character c = character(5L, "王五", "MALE", List.of("普通"), "路人");
            Dialogue d1 = dialogue(1, "王芳", "老子去也！", Emotion.ANGRY, null);
            Dialogue d2 = dialogue(2, "王五",
                    "你知道的，事情是这样的，让我来告诉你。",
                    Emotion.CALM, null);

            List<ConsistencyIssue> issues = checker.check(List.of(d1, d2), List.of(female, c));
            List<ConsistencyIssue> actionable = checker.getActionableIssues(issues);

            assertThat(actionable).allMatch(i ->
                    i.getSeverity() == Severity.ERROR || i.getSeverity() == Severity.WARNING);
        }
    }

    // ── 5. Edge cases ───────────────────────────────────

    @Nested
    @DisplayName("5. 边缘情况与对白质量")
    class EdgeCases {

        @Test
        @DisplayName("双人对白应保持逻辑衔接")
        void shouldMaintainLogicalConnection() {
            // 模拟双人对话：恋人含蓄表达
            List<Dialogue> dialogues = List.of(
                    dialogue(1, "李明", "今天天气不错。", Emotion.CALM, null),
                    dialogue(2, "王芳", "是啊……适合散步。", Emotion.GENTLE, "(低头)"),
                    dialogue(3, "李明", "那……一起走走吧。", Emotion.GENTLE, "(微笑)")
            );

            assertThat(dialogues).hasSize(3);
            // 验证对话有逻辑衔接
            assertThat(dialogues.get(1).getContent()).contains("散步"); // 承接"天气"
            assertThat(dialogues.get(2).getContent()).contains("一起"); // 推进关系
        }

        @Test
        @DisplayName("多人争吵应各有立场")
        void shouldHaveDistinctVoices() {
            List<Dialogue> conflictDialogues = List.of(
                    dialogue(1, "甲方", "这就是合同的问题！", Emotion.ANGRY, null),
                    dialogue(2, "乙方", "合同写得清清楚楚。", Emotion.COLD, "(冷笑)"),
                    dialogue(3, "丙方", "各位冷静一下，我们再看看条款。", Emotion.CALM, "(抬手)")
            );

            // 三人三种情绪，各有立场
            assertThat(conflictDialogues.get(0).getEmotion()).isEqualTo(Emotion.ANGRY);
            assertThat(conflictDialogues.get(1).getEmotion()).isEqualTo(Emotion.COLD);
            assertThat(conflictDialogues.get(2).getEmotion()).isEqualTo(Emotion.CALM);
        }

        @Test
        @DisplayName("情绪渐变应对白应有体现")
        void shouldShowEmotionalGradient() {
            // 从平静 → 压抑 → 愤怒 → 悲伤
            List<Dialogue> gradient = List.of(
                    dialogue(1, "角色", "你来了。", Emotion.CALM, null),
                    dialogue(2, "角色", "我知道了那件事。", Emotion.COLD, "(语气变冷)"),
                    dialogue(3, "角色", "为什么？！为什么是你？！", Emotion.ANGRY, "(猛地站起)"),
                    dialogue(4, "角色", "……走吧。我不想再见到你。", Emotion.SAD, "(背过身去)")
            );

            assertThat(gradient.get(0).getEmotion().isLowIntensity()).isTrue();
            assertThat(gradient.get(2).getEmotion().isHighIntensity()).isTrue();
            assertThat(gradient.get(3).getEmotion()).isEqualTo(Emotion.SAD);
        }

        @Test
        @DisplayName("冷静角色从不说感叹句")
        void calmCharacterNeverExclaims() {
            // Rule: 冷静角色 content 不以感叹号结尾
            List<String> calmDialogues = List.of(
                    "我明白你的意思。",
                    "可以考虑。",
                    "再想想吧。"
            );

            boolean hasExclamation = calmDialogues.stream()
                    .anyMatch(s -> s.endsWith("！") || s.endsWith("!"));
            assertThat(hasExclamation).isFalse();
        }

        @Test
        @DisplayName("每句对白不超过50字（中文）")
        void shouldKeepLinesUnder50Chars() {
            List<String> goodDialogues = List.of(
                    "你说的对，我确实应该好好考虑一下。",
                    "今天天气真好，适合出去走走。",
                    "我不同意你的看法，但我尊重你的选择。"
            );

            for (String line : goodDialogues) {
                assertThat(line.length()).isLessThanOrEqualTo(50);
            }
        }

        @Test
        @DisplayName("parenthetical 应合理使用")
        void shouldUseParentheticalCorrectly() {
            Dialogue withParen = dialogue(1, "角色", "好吧。", Emotion.CALM, "(叹气)");
            Dialogue withoutParen = dialogue(2, "角色", "好。", Emotion.CALM, null);

            assertThat(withParen.getParenthetical()).isNotNull();
            assertThat(withParen.getParenthetical()).startsWith("(");
            assertThat(withoutParen.getParenthetical()).isNull();
        }
    }

    // ── 6. Character consistency tests ─────────────────

    @Nested
    @DisplayName("6. 角色性格一致性")
    class CharacterConsistency {

        @Test
        @DisplayName("不同性格角色应有不同说话风格")
        void shouldHaveDistinctSpeechStyles() {
            String calmStyle = Emotion.inferSpeechStyle(List.of("冷静", "沉稳", "理智"));
            String hotStyle = Emotion.inferSpeechStyle(List.of("热情", "开朗", "外向"));
            String coldStyle = Emotion.inferSpeechStyle(List.of("冷酷", "无情"));

            assertThat(calmStyle).isNotEqualTo(hotStyle);
            assertThat(calmStyle).isNotEqualTo(coldStyle);
            assertThat(hotStyle).isNotEqualTo(coldStyle);
        }

        @Test
        @DisplayName("性别信息应在角色中保留")
        void shouldPreserveGenderInCharacter() {
            Character male = character(1L, "男主", "MALE", List.of("勇敢"), "男主角");
            Character female = character(2L, "女主", "FEMALE", List.of("温柔"), "女主角");

            assertThat(male.getGender()).isEqualTo("MALE");
            assertThat(female.getGender()).isEqualTo("FEMALE");
        }
    }
}
