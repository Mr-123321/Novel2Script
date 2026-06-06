package com.novel2script.application.service.agent;

import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.common.enums.CharacterRoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CharacterNameFilter}.
 */
@DisplayName("CharacterNameFilter")
class CharacterNameFilterTest {

    // ── Helper ────────────────────────────────────────────

    private static CharacterExtractionResult makeChar(String name, String desc) {
        return new CharacterExtractionResult(
                name, Collections.emptyList(), CharacterRoleType.SUPPORTING,
                null, null, desc,
                Collections.emptyList(), Collections.emptyList(),
                null, 0);
    }

    private static CharacterExtractionResult makeChar(String name,
                                                       CharacterRoleType role,
                                                       String desc) {
        return new CharacterExtractionResult(
                name, Collections.emptyList(), role,
                null, null, desc,
                Collections.emptyList(), Collections.emptyList(),
                null, 0);
    }

    // ── Tier 1: Hard reject ───────────────────────────────

    @Nested
    @DisplayName("Tier 1 — 硬拒绝（一定不是人名）")
    class Tier1HardReject {

        @Test
        @DisplayName("包含中文标点的名称应被过滤")
        void shouldRejectPunctuationInName() {
            // The exact case from the bug report
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("到来，平静应", "一个角色")));

            // Other punctuation variants
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("平静。地说", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("忽然！一声", "描述")));
            // Name containing ASCII whitespace
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("he looks at her", "clearly not a name")));
        }

        @Test
        @DisplayName("过长的名称（≥6字）应被过滤")
        void shouldRejectTooLongName() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("这是一个很长的名字", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("平静地说道因为", "描述")));
        }

        @Test
        @DisplayName("空名称和 null entry 应被过滤")
        void shouldRejectBlankOrNull() {
            assertTrue(CharacterNameFilter.isNoise(null));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("   ", "描述")));
        }
    }

    // ── Tier 2: Strong reject ─────────────────────────────

    @Nested
    @DisplayName("Tier 2 — 强拒绝（句子片段模式）")
    class Tier2StrongReject {

        @Test
        @DisplayName("以说话/动作字结尾的名称应被过滤")
        void shouldRejectSpeechActionSuffix() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("平静道", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("笑着说", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("问道", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("喊道", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("答道", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("骂道", "描述")));
        }

        @Test
        @DisplayName("是常见动词的名称应被过滤")
        void shouldRejectCommonVerb() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("到来", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("离开", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("走过", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("说道", "描述")));
        }

        @Test
        @DisplayName("是常见形容词/副词的名词应被过滤")
        void shouldRejectCommonAdjective() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("平静", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("忽然", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("慢慢", "描述")));
        }

        @Test
        @DisplayName("是功能词/虚词的名称应被过滤")
        void shouldRejectFunctionWord() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("所以", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("但是", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("今天", "描述")));
        }

        @Test
        @DisplayName("是常见非人名词的名称应被过滤")
        void shouldRejectNonNameNoun() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("众人", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("大家", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("年轻人", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("士兵", "描述")));
        }

        @Test
        @DisplayName("形容词/动词 + 地/得/的/了/着/过 结尾应被过滤")
        void shouldRejectAdjVerbWithParticle() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("平静地", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("狠狠地说", "有描述的情况")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("安静了", "描述")));
        }

        @Test
        @DisplayName("以虚词开头的多字名称应被过滤")
        void shouldRejectFunctionWordPrefix() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("一个人", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("这个人", "描述")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("没有人知道", "描述")));
        }
    }

    // ── Tier 3: Weak reject (combined signals) ────────────

    @Nested
    @DisplayName("Tier 3 — 弱拒绝（组合信号）")
    class Tier3WeakReject {

        @Test
        @DisplayName("4-5字名称 + 无姓氏 + 弱描述应被过滤")
        void shouldRejectLongNameNoSurnameWeakDesc() {
            // "慢慢走来" — 4 chars, no surname, weak description
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("慢慢走来", "一个角色")));
        }

        @Test
        @DisplayName("副词开头 + 无姓氏 + 弱描述的2-3字名称应被过滤")
        void shouldRejectAdverbialStartNoSurnameWeakDesc() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("忽然", "路人")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("猛然", "未知")));
        }

        @Test
        @DisplayName("单字名 + 弱描述应被过滤")
        void shouldRejectSingleCharWeakDesc() {
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("道", "路人")));
            assertTrue(CharacterNameFilter.isNoise(
                    makeChar("说", "")));
        }
    }

    // ── Legitimate names — should PASS ────────────────────

    @Nested
    @DisplayName("合法名称 — 应通过过滤")
    class LegitimateNames {

        @Test
        @DisplayName("标准中文姓名应通过")
        void shouldPassStandardChineseNames() {
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("林川", "大学生穿越者，拥有现代知识")));
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("萧炎", "天才少年，修炼斗气")));
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("叶凡", "平凡的少年，身怀绝世体质")));
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("王小明", "普通的中学生，性格开朗")));
        }

        @Test
        @DisplayName("三字姓名应通过")
        void shouldPassThreeCharNames() {
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("李世民", "唐朝皇帝，雄才大略")));
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("张三丰", "武当派创始人，武功高强")));
        }

        @Test
        @DisplayName("复姓姓名应通过")
        void shouldPassCompoundSurnames() {
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("慕容雪", "慕容世家的天才少女")));
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("欧阳锋", "西域白驼山庄主")));
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("上官婉儿", "才女，精通诗书")));
        }

        @Test
        @DisplayName("称号/昵称类名称应通过")
        void shouldPassTitleStyleNames() {
            // "幽冥老人" is a title — 4 chars, no common surname, but has good description
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("幽冥老人", "神秘的老者，实力深不可测")));
            // "药老" — 2 chars, nickname
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("药老", "炼丹大师，萧炎的师父")));
        }

        @Test
        @DisplayName("有良好描述的4字名称应通过（即使无常见姓氏）")
        void shouldPassFourCharNameWithGoodDescription() {
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("云岚宗主", "云岚宗的掌门人，威严深沉")));
        }

        @Test
        @DisplayName("有良好描述的单字名应通过")
        void shouldPassSingleCharNameWithGoodDescription() {
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("云", "神秘的流浪剑客，名字只有一个字")));
        }

        @Test
        @DisplayName("英文名（合理音译）应通过")
        void shouldPassTransliteratedNames() {
            // "Tom" is a real name
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("Tom", "外国留学生，来自美国")));
        }
    }

    // ── Edge cases ────────────────────────────────────────

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("含姓氏的3字形容词模式不应被误杀")
        void shouldNotKillNameThatLooksLikeAdjectiveButHasSurname() {
            // "林静" — 林 is a surname, even though "静" is an adjective
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("林静", "安静的女学生")));
        }

        @Test
        @DisplayName("filter 方法返回空列表而非 null")
        void shouldReturnEmptyListNotNul() {
            List<CharacterExtractionResult> result = CharacterNameFilter.filter(
                    List.of(makeChar("众人", "desc"),
                            makeChar("大家", "desc")));
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("filter 方法对 null 输入返回空列表")
        void shouldHandleNullInput() {
            assertTrue(CharacterNameFilter.filter(null).isEmpty());
        }

        @Test
        @DisplayName("filter 方法对空列表返回空列表")
        void shouldHandleEmptyInput() {
            assertTrue(CharacterNameFilter.filter(Collections.emptyList()).isEmpty());
        }

        @Test
        @DisplayName("混合列表：保留合法项，过滤噪声项")
        void shouldFilterMixedList() {
            List<CharacterExtractionResult> input = List.of(
                    makeChar("林川", "主角"),
                    makeChar("到来，平静应", "噪声"),           // punctuation → reject
                    makeChar("萧炎", "天才少年"),
                    makeChar("平静道", "噪声"),                  // speech suffix → reject
                    makeChar("众人", "噪声"),                    // common noun → reject
                    makeChar("慕容雪", "复姓名")
            );

            List<CharacterExtractionResult> result = CharacterNameFilter.filter(input);

            assertEquals(3, result.size());
            assertThat(result.stream().map(CharacterExtractionResult::name))
                    .containsExactly("林川", "萧炎", "慕容雪");
        }

        @Test
        @DisplayName("主角类型也不应被误过滤")
        void shouldPassProtagonists() {
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("林川", CharacterRoleType.PROTAGONIST,
                            "大学生穿越者")));
        }

        @Test
        @DisplayName("含'道'字的合法名不应被误杀（如'道玄'）")
        void shouldPassNameEndingWithDaoIfHasSurname() {
            // "道" at the end triggers speech suffix check,
            // but "道玄" has 玄 which looks like a given name
            // Actually "道玄" ends with 玄, not 道. Let me test "陈道"
            // "陈道" — 陈 is surname, 道 is given name (rare but possible)
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("陈道", "修道之人，性格沉稳")));
        }

        @Test
        @DisplayName("名称以'道'字结尾但有丰富描述的不应被误杀（法号/道号）")
        void shouldNotRejectDaoWithRichDescription() {
            // "悟道" is a Buddhist Dharma name — should pass with rich description
            assertFalse(CharacterNameFilter.isNoise(
                    makeChar("悟道", "少林寺高僧，佛法精深")));
        }
    }

    // ── hasCommonSurname tests ─────────────────────────────

    @Nested
    @DisplayName("hasCommonSurname — 姓氏检测")
    class SurnameDetection {

        @Test
        @DisplayName("常见单字姓氏应被识别")
        void shouldDetectCommonSingleSurnames() {
            assertTrue(CharacterNameFilter.hasCommonSurname("林川"));
            assertTrue(CharacterNameFilter.hasCommonSurname("李雪"));
            assertTrue(CharacterNameFilter.hasCommonSurname("王小明"));
            assertTrue(CharacterNameFilter.hasCommonSurname("张三"));
        }

        @Test
        @DisplayName("复姓应被识别")
        void shouldDetectCompoundSurnames() {
            assertTrue(CharacterNameFilter.hasCommonSurname("慕容雪"));
            assertTrue(CharacterNameFilter.hasCommonSurname("欧阳锋"));
            assertTrue(CharacterNameFilter.hasCommonSurname("上官婉儿"));
        }

        @Test
        @DisplayName("非姓氏开头不应被识别为有姓氏")
        void shouldNotDetectNonSurnames() {
            assertFalse(CharacterNameFilter.hasCommonSurname("到来"));
            assertFalse(CharacterNameFilter.hasCommonSurname("平静"));
            assertFalse(CharacterNameFilter.hasCommonSurname("忽然"));
            assertFalse(CharacterNameFilter.hasCommonSurname("众人"));
        }

        @Test
        @DisplayName("单字名不应有姓氏")
        void shouldNotDetectSurnameInSingleChar() {
            assertFalse(CharacterNameFilter.hasCommonSurname("道"));
            assertFalse(CharacterNameFilter.hasCommonSurname("云"));
        }
    }

    // ── hasWeakDescription tests ───────────────────────────

    @Nested
    @DisplayName("hasWeakDescription — 弱描述检测")
    class WeakDescription {

        @Test
        @DisplayName("null 或空描述视为弱")
        void shouldFlagNullOrBlankDescription() {
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("test", null)));
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("test", "")));
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("test", "   ")));
        }

        @Test
        @DisplayName("过短描述（<5字）视为弱")
        void shouldFlagVeryShortDescription() {
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("test", "路人")));
        }

        @Test
        @DisplayName("仅重复名称的描述视为弱")
        void shouldFlagDescriptionThatJustRepeatsName() {
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("到来", "到来的人")));
        }

        @Test
        @DisplayName("极通用的描述视为弱")
        void shouldFlagGenericDescription() {
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("test", "主角")));
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("test", "路人")));
            assertTrue(CharacterNameFilter.hasWeakDescription(
                    makeChar("test", "未知")));
        }

        @Test
        @DisplayName("有意义的描述不应视为弱")
        void shouldNotFlagMeaningfulDescription() {
            assertFalse(CharacterNameFilter.hasWeakDescription(
                    makeChar("林川", "大学生穿越者，拥有现代知识")));
            assertFalse(CharacterNameFilter.hasWeakDescription(
                    makeChar("萧炎", "天才少年，修炼斗气")));
        }
    }
}
