package com.novel2script.application.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.application.service.agent.model.ExtractedRelationship;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.domain.model.Character;
import com.novel2script.infrastructure.vector.EmbeddingService;
import com.novel2script.infrastructure.vector.MilvusVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CharacterResolverAgent} focusing on
 * rule-based name matching, grouping logic, Levenshtein distance,
 * and the full resolution pipeline.
 */
@DisplayName("CharacterResolverAgent")
class CharacterResolverAgentTest {

    private CharacterResolverAgent agent;
    private MilvusVectorStore vectorStore;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        vectorStore = new MilvusVectorStore();
        embeddingService = new EmbeddingService(null);
        // Only rule-based + embedding layers (no LLM in unit tests)
        agent = new CharacterResolverAgent(vectorStore, embeddingService,
                null, null, new ObjectMapper(), false);
    }

    // ── Chinese name analysis ────────────────────────────

    @Nested
    @DisplayName("规则匹配 — 中国人名分析")
    class ChineseNameAnalysis {

        @Test
        @DisplayName("同姓别名: 林川 + 川哥 → 同一人")
        void shouldMatchSurnameAlias() {
            assertTrue(CharacterResolverAgent.isSamePersonByRules("林川", "川哥"));
        }

        @Test
        @DisplayName("同姓前缀: 林川 + 小川 → 同一人")
        void shouldMatchPrefixAlias() {
            assertTrue(CharacterResolverAgent.isSamePersonByRules("林川", "小川"));
        }

        @Test
        @DisplayName("名+称谓: 李雪 + 雪儿 → 同一人")
        void shouldMatchNameHonorific() {
            assertTrue(CharacterResolverAgent.isSamePersonByRules("李雪", "雪儿"));
        }

        @Test
        @DisplayName("完全包含: 张大壮 + 大壮 → 同一人")
        void shouldMatchSubstring() {
            assertTrue(CharacterResolverAgent.isSamePersonByRules("张大壮", "大壮"));
        }

        @Test
        @DisplayName("姓氏+称谓: 林川 + 林师兄 → 同一人")
        void shouldMatchSurnameTitle() {
            assertTrue(CharacterResolverAgent.isSamePersonByRules("林川", "林师兄"));
        }

        @Test
        @DisplayName("不同人: 林川 + 王磊 → 不同人")
        void shouldNotMatchDifferentPeople() {
            assertFalse(CharacterResolverAgent.isSamePersonByRules("林川", "王磊"));
        }

        @Test
        @DisplayName("不同人: 林川 + 李雪 → 不同人")
        void shouldNotMatchDifferentSurname() {
            assertFalse(CharacterResolverAgent.isSamePersonByRules("林川", "李雪"));
        }

        @Test
        @DisplayName("老+姓: 老王 + 王明 → 同一人")
        void shouldMatchLaoPrefix() {
            assertTrue(CharacterResolverAgent.isSamePersonByRules("老王", "王明"));
        }

        @Test
        @DisplayName("简称匹配: 婉儿 + 林婉儿 → 同一人")
        void shouldMatchShortForm() {
            assertTrue(CharacterResolverAgent.isSamePersonByRules("婉儿", "林婉儿"));
        }
    }

    // ── Base name extraction ──────────────────────────────

    @Nested
    @DisplayName("extractBaseName — 基名提取")
    class BaseNameExtraction {

        @Test
        @DisplayName("川哥 → 川")
        void shouldExtractSuffix() {
            assertEquals("川", CharacterResolverAgent.extractBaseName("川哥"));
        }

        @Test
        @DisplayName("小川 → 川")
        void shouldExtractPrefix() {
            assertEquals("川", CharacterResolverAgent.extractBaseName("小川"));
        }

        @Test
        @DisplayName("林师兄 → 林")
        void shouldExtractCompoundSuffix() {
            assertEquals("林", CharacterResolverAgent.extractBaseName("林师兄"));
        }

        @Test
        @DisplayName("阿明 → 明")
        void shouldExtractAPrefix() {
            assertEquals("明", CharacterResolverAgent.extractBaseName("阿明"));
        }

        @Test
        @DisplayName("老张 → 张")
        void shouldExtractLaoPrefix() {
            assertEquals("张", CharacterResolverAgent.extractBaseName("老张"));
        }

        @Test
        @DisplayName("单字不变: 林川 → 林川")
        void shouldKeepFullName() {
            assertEquals("林川", CharacterResolverAgent.extractBaseName("林川"));
        }

        @Test
        @DisplayName("姐后缀: 雪姐 → 雪")
        void shouldExtractJie() {
            assertEquals("雪", CharacterResolverAgent.extractBaseName("雪姐"));
        }
    }

    // ── Levenshtein distance ──────────────────────────────

    @Nested
    @DisplayName("编辑距离")
    class LevenshteinTests {

        @Test
        @DisplayName("相同字符串距离为0")
        void shouldBeZeroForSame() {
            assertEquals(0, CharacterResolverAgent.levenshteinDistance("林川", "林川"));
        }

        @Test
        @DisplayName("单字符差异距离为1")
        void shouldBeOneForSingleDiff() {
            assertEquals(1, CharacterResolverAgent.levenshteinDistance("林川", "林州"));
        }

        @Test
        @DisplayName("完全不同距离较大")
        void shouldBeLargeForDifferent() {
            assertTrue(CharacterResolverAgent.levenshteinDistance("林川", "王大明") >= 2);
        }

        @Test
        @DisplayName("林婉儿 vs 婉儿 — 距离应合理")
        void shouldHandlePrefixDifference() {
            int dist = CharacterResolverAgent.levenshteinDistance("林婉儿", "婉儿");
            assertEquals(1, dist, "Removing one char (林) → distance should be 1");
        }
    }

    // ── Rule-based grouping ───────────────────────────────

    @Nested
    @DisplayName("ruleBasedGrouping — 规则分组")
    class RuleBasedGroupingTests {

        @Test
        @DisplayName("同姓别名应分到同一组")
        void shouldGroupAliases() {
            List<CharacterExtractionResult> characters = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "穿越者大学生"),
                    createResult("川哥", CharacterRoleType.PROTAGONIST, "林川的别称"),
                    createResult("小川", CharacterRoleType.PROTAGONIST, "林川的小名"),
                    createResult("李雪", CharacterRoleType.SUPPORTING, "女主角"),
                    createResult("王磊", CharacterRoleType.SUPPORTING, "林川的朋友")
            );

            List<List<CharacterExtractionResult>> groups =
                    agent.ruleBasedGrouping(characters);

            // 林川, 川哥, 小川 should be in one group
            // 李雪 and 王磊 should each be in their own groups
            assertEquals(3, groups.size(),
                    "Expected 3 groups: (林川+川哥+小川), (李雪), (王磊)");

            // Find the group containing 林川
            List<CharacterExtractionResult> linChuanGroup = groups.stream()
                    .filter(g -> g.stream().anyMatch(c -> c.name().equals("林川")))
                    .findFirst().orElseThrow();

            assertEquals(3, linChuanGroup.size(),
                    "林川 group should have 3 members: 林川, 川哥, 小川");
            assertTrue(linChuanGroup.stream().anyMatch(c -> c.name().equals("川哥")));
            assertTrue(linChuanGroup.stream().anyMatch(c -> c.name().equals("小川")));
        }

        @Test
        @DisplayName("姓+称谓应分到同一组")
        void shouldGroupSurnameTitles() {
            List<CharacterExtractionResult> characters = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "主角"),
                    createResult("林师兄", CharacterRoleType.SUPPORTING, "林川的师兄")
            );

            List<List<CharacterExtractionResult>> groups =
                    agent.ruleBasedGrouping(characters);

            assertEquals(1, groups.size(),
                    "林川 and 林师兄 should be in the same group");
        }

        @Test
        @DisplayName("不同人不应分到同一组")
        void shouldNotGroupDifferentPeople() {
            List<CharacterExtractionResult> characters = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "主角"),
                    createResult("李雪", CharacterRoleType.SUPPORTING, "女主角"),
                    createResult("王磊", CharacterRoleType.SUPPORTING, "配角"),
                    createResult("张大壮", CharacterRoleType.MINOR, "路人")
            );

            List<List<CharacterExtractionResult>> groups =
                    agent.ruleBasedGrouping(characters);

            assertEquals(4, groups.size(),
                    "All 4 should be in separate groups");
        }

        @Test
        @DisplayName("完全包含应分到同一组: 张大壮 + 大壮")
        void shouldGroupContainedNames() {
            List<CharacterExtractionResult> characters = List.of(
                    createResult("张大壮", CharacterRoleType.PROTAGONIST, "主角"),
                    createResult("大壮", CharacterRoleType.PROTAGONIST, "主角的简称")
            );

            List<List<CharacterExtractionResult>> groups =
                    agent.ruleBasedGrouping(characters);

            assertEquals(1, groups.size());
        }
    }

    // ── Full resolution pipeline ─────────────────────────

    @Nested
    @DisplayName("resolve — 完整去重流程")
    class FullResolutionTests {

        @Test
        @DisplayName("林川+川哥+小川 → 合并为1个角色")
        void shouldMergeLinChuanAliases() {
            List<CharacterExtractionResult> characters = List.of(
                    new CharacterExtractionResult("林川",
                            List.of(), CharacterRoleType.PROTAGONIST,
                            "男", "20-25", "大学生穿越者，拥有现代知识",
                            List.of("理性", "好奇心强"),
                            List.of(new ExtractedRelationship("李雪", "恋人")),
                            "第1章", 10),
                    new CharacterExtractionResult("川哥",
                            List.of(), CharacterRoleType.PROTAGONIST,
                            "男", "20-25", "林川的别称",
                            List.of(),
                            List.of(),
                            "第2章", 3),
                    new CharacterExtractionResult("小川",
                            List.of(), CharacterRoleType.PROTAGONIST,
                            "男", "20-25", "林川的小名",
                            List.of(),
                            List.of(),
                            "第3章", 2)
            );

            List<Character> resolved = agent.resolve(characters);

            // Should merge to 1 character
            assertEquals(1, resolved.size());
            Character linChuan = resolved.get(0);
            assertEquals("林川", linChuan.getCanonicalName());
            assertTrue(linChuan.isResolved());
            // Should have collected aliases
            assertTrue(linChuan.getAliases().size() >= 2,
                    "Should have at least aliases from merged characters");
            assertTrue(linChuan.getAliases().contains("川哥")
                    || linChuan.getAliases().contains("小川"));
        }

        @Test
        @DisplayName("不同人物保持独立")
        void shouldKeepDifferentCharactersSeparate() {
            List<CharacterExtractionResult> characters = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "主角"),
                    createResult("李雪", CharacterRoleType.SUPPORTING, "女主角"),
                    createResult("王磊", CharacterRoleType.SUPPORTING, "林川好友"),
                    createResult("幽冥老人", CharacterRoleType.ANTAGONIST, "反派BOSS")
            );

            List<Character> resolved = agent.resolve(characters);

            assertEquals(4, resolved.size());
            List<String> names = resolved.stream()
                    .map(Character::getCanonicalName)
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("林川", "李雪", "王磊", "幽冥老人");
        }

        @Test
        @DisplayName("空角色列表返回空结果")
        void shouldReturnEmptyForNoCharacters() {
            assertTrue(agent.resolve(Collections.emptyList()).isEmpty());
            assertTrue(agent.resolve(null).isEmpty());
        }

        @Test
        @DisplayName("单角色直接返回")
        void shouldReturnSingleCharacterAsIs() {
            List<CharacterExtractionResult> characters = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "主角")
            );

            List<Character> resolved = agent.resolve(characters);

            assertEquals(1, resolved.size());
            assertEquals("林川", resolved.get(0).getCanonicalName());
            assertTrue(resolved.get(0).isResolved());
        }
    }

    // ── Embedding-based merging ──────────────────────────

    @Nested
    @DisplayName("向量相似度合并")
    class EmbeddingMergeTests {

        @Test
        @DisplayName("相似描述应合并为同一角色")
        void shouldMergeSimilarDescriptions() {
            vectorStore.clear();

            List<CharacterExtractionResult> characters = List.of(
                    new CharacterExtractionResult("林川",
                            List.of(), CharacterRoleType.PROTAGONIST,
                            "男", "20-25", "大学生穿越者主角",
                            List.of("理性"), List.of(),
                            "第1章", 10),
                    new CharacterExtractionResult("川哥",
                            List.of(), CharacterRoleType.PROTAGONIST,
                            "男", "20-25", "大学生穿越者",
                            List.of("好奇心强"), List.of(),
                            "第2章", 3)
            );

            List<Character> resolved = agent.resolve(characters);

            // Should detect as same person via rules OR embedding
            assertEquals(1, resolved.size(),
                    "Similar names + descriptions should merge to 1 character");
        }

        @Test
        @DisplayName("完全不同的描述应保持独立")
        void shouldKeepDifferentDescriptionsSeparate() {
            vectorStore.clear();

            List<CharacterExtractionResult> characters = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST,
                            "大学生意外穿越到异世界，拥有现代知识和特殊能力"),
                    createResult("幽冥老人", CharacterRoleType.ANTAGONIST,
                            "修炼千年的反派大BOSS，掌控黑暗势力")
            );

            List<Character> resolved = agent.resolve(characters);

            assertEquals(2, resolved.size(),
                    "Completely different characters should not merge");
        }
    }

    // ── Helper ────────────────────────────────────────────

    private static CharacterExtractionResult createResult(
            String name, CharacterRoleType roleType, String description) {
        return new CharacterExtractionResult(
                name, Collections.emptyList(), roleType,
                null, null, description,
                Collections.emptyList(), Collections.emptyList(),
                null, 0);
    }
}
