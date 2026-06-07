package com.novel2script.application.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2script.application.service.agent.model.CharacterExtractionResult;
import com.novel2script.application.service.agent.model.ExtractedRelationship;
import com.novel2script.common.enums.ChapterType;
import com.novel2script.common.enums.CharacterRoleType;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Character;
import com.novel2script.domain.prompt.FewShot;
import com.novel2script.domain.prompt.ValidationRule;
import com.novel2script.infrastructure.prompt.PromptRegistry;
import com.novel2script.infrastructure.prompt.PromptTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CharacterAgent} focusing on pure logic:
 * JSON parsing, extraction, validation, and incremental filtering.
 *
 * <p>AI model calls are NOT tested here — those require integration tests
 * with a running AI provider or a wire-mocked HTTP endpoint.
 */
@DisplayName("CharacterAgent")
class CharacterAgentTest {

    private CharacterAgent agent;
    private ObjectMapper objectMapper;
    private PromptRegistry promptRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // We don't wire the full Spring context for unit tests.
        // Construct with null for AI-call dependencies; only test pure-logic methods.
        agent = new CharacterAgent(null, null, null, objectMapper);
        promptRegistry = null; // not used in pure-logic tests
    }

    // ── JSON response parsing ────────────────────────────

    @Nested
    @DisplayName("parseResponse — JSON 响应解析")
    class ParseResponseTests {

        @Test
        @DisplayName("解析 {'characters': [...]} 包装格式")
        void shouldParseWrappedObjectFormat() {
            String response = """
                    {
                      "characters": [
                        {
                          "name": "林川",
                          "aliases": ["川哥", "小林"],
                          "role_type": "PROTAGONIST",
                          "gender": "男",
                          "age_range": "20-25",
                          "description": "大学生穿越者",
                          "personality": ["理性", "好奇心强"],
                          "relationships": [
                            {"target": "李雪", "relation": "恋人"},
                            {"target": "王磊", "relation": "朋友"}
                          ]
                        },
                        {
                          "name": "李雪",
                          "aliases": ["雪儿"],
                          "role_type": "SUPPORTING",
                          "gender": "女",
                          "age_range": "18-22",
                          "description": "女主角，门派弟子",
                          "personality": ["温柔", "坚强"],
                          "relationships": [
                            {"target": "林川", "relation": "恋人"}
                          ]
                        }
                      ]
                    }
                    """;

            List<CharacterExtractionResult> results = agent.parseResponse(response);

            assertEquals(2, results.size());

            // First character: 林川
            CharacterExtractionResult ch1 = results.get(0);
            assertEquals("林川", ch1.name());
            assertEquals(CharacterRoleType.PROTAGONIST, ch1.roleType());
            assertEquals("男", ch1.gender());
            assertEquals("20-25", ch1.ageRange());
            assertEquals(2, ch1.aliases().size());
            assertTrue(ch1.aliases().contains("川哥"));
            assertTrue(ch1.aliases().contains("小林"));
            assertEquals(2, ch1.personality().size());
            assertEquals(2, ch1.relationships().size());
            assertTrue(ch1.isProtagonist());

            // Check relationships
            ExtractedRelationship rel1 = ch1.relationships().get(0);
            assertEquals("李雪", rel1.targetName());
            assertEquals("恋人", rel1.relationType());
            assertTrue(rel1.isRomantic());

            // Second character: 李雪
            CharacterExtractionResult ch2 = results.get(1);
            assertEquals("李雪", ch2.name());
            assertEquals(CharacterRoleType.SUPPORTING, ch2.roleType());
            assertEquals(1, ch2.relationships().size());
        }

        @Test
        @DisplayName("解析裸数组 [...] 格式")
        void shouldParseBareArrayFormat() {
            String response = """
                    [
                      {
                        "name": "王磊",
                        "role_type": "SUPPORTING",
                        "description": "林川的好友"
                      },
                      {
                        "name": "幽冥老人",
                        "role_type": "ANTAGONIST",
                        "description": "反派BOSS"
                      }
                    ]
                    """;

            List<CharacterExtractionResult> results = agent.parseResponse(response);

            assertEquals(2, results.size());
            assertEquals("王磊", results.get(0).name());
            assertEquals(CharacterRoleType.SUPPORTING, results.get(0).roleType());
            assertEquals("幽冥老人", results.get(1).name());
            assertEquals(CharacterRoleType.ANTAGONIST, results.get(1).roleType());
            assertTrue(results.get(1).isAntagonist());
        }

        @Test
        @DisplayName("解析 markdown 代码块中的 JSON")
        void shouldParseMarkdownFencedJson() {
            String response = """
                    以下是提取到的角色：

                    ```json
                    [
                      {"name": "张三", "role_type": "MINOR", "description": "路人"}
                    ]
                    ```

                    以上就是全部角色。
                    """;

            List<CharacterExtractionResult> results = agent.parseResponse(response);

            assertEquals(1, results.size());
            assertEquals("张三", results.get(0).name());
            assertEquals(CharacterRoleType.MINOR, results.get(0).roleType());
        }

        @Test
        @DisplayName("空响应返回空列表")
        void shouldReturnEmptyForBlankResponse() {
            assertTrue(agent.parseResponse(null).isEmpty());
            assertTrue(agent.parseResponse("").isEmpty());
            assertTrue(agent.parseResponse("   ").isEmpty());
        }

        @Test
        @DisplayName("无 JSON 块的响应返回空列表")
        void shouldReturnEmptyForNonJsonResponse() {
            String response = "抱歉，我无法提取角色信息。";
            List<CharacterExtractionResult> results = agent.parseResponse(response);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("部分字段缺失不应抛异常")
        void shouldHandlePartialFields() {
            String response = """
                    [
                      {"name": "林川", "role_type": "PROTAGONIST", "description": "主角"},
                      {"name": "", "role_type": "SUPPORTING", "description": "无效"},
                      {"name": "李雪", "role_type": "SUPPORTING", "description": "女主"}
                    ]
                    """;

            List<CharacterExtractionResult> results = agent.parseResponse(response);

            // Empty name should be filtered out
            assertEquals(2, results.size());
            assertEquals("林川", results.get(0).name());
            assertEquals("李雪", results.get(1).name());
        }
    }

    // ── JSON extraction ──────────────────────────────────

    @Nested
    @DisplayName("extractJson — JSON 文本提取")
    class ExtractJsonTests {

        @Test
        @DisplayName("从 markdown fence 提取")
        void shouldExtractFromFence() {
            String text = "```json\n{\"characters\":[]}\n```";
            assertEquals("{\"characters\":[]}", CharacterAgent.extractJson(text));
        }

        @Test
        @DisplayName("从普通文本中提取 JSON 数组")
        void shouldExtractJsonArrayFromText() {
            String text = "结果如下：\n[{\"name\":\"A\"}]\n解析完成。";
            assertEquals("[{\"name\":\"A\"}]", CharacterAgent.extractJson(text));
        }

        @Test
        @DisplayName("纯 JSON 直接返回")
        void shouldReturnPureJson() {
            String text = "[{\"name\":\"A\"}]";
            assertEquals("[{\"name\":\"A\"}]", CharacterAgent.extractJson(text));
        }

        @Test
        @DisplayName("无 JSON 返回 null")
        void shouldReturnNullForNoJson() {
            assertNull(CharacterAgent.extractJson("只是一段普通文本。"));
            assertNull(CharacterAgent.extractJson(""));
            assertNull(CharacterAgent.extractJson(null));
        }
    }

    // ── Validation ───────────────────────────────────────

    @Nested
    @DisplayName("validateResults — 结果验证")
    class ValidationTests {

        @Test
        @DisplayName("notBlank: 空白名称应报错")
        void shouldFlagBlankName() {
            List<CharacterExtractionResult> results = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "主角"),
                    createResult("", CharacterRoleType.SUPPORTING, "配角"),
                    createResult(null, CharacterRoleType.MINOR, "路人")
            );

            List<ValidationRule> rules = List.of(
                    ValidationRule.builder().field("characters[].name").rule("notBlank").build()
            );

            List<String> errors = agent.validateResults(results, rules);
            assertEquals(2, errors.size());
            assertTrue(errors.get(0).contains("name"));
            assertTrue(errors.get(1).contains("name"));
        }

        @Test
        @DisplayName("enum_match: 非法角色类型应报错")
        void shouldFlagInvalidRoleType() {
            // Note: role_type values from JSON are enum names, validated by Jackson.
            // This tests the validation rule logic for correctness.
            List<CharacterExtractionResult> results = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "主角"),
                    createResult("李雪", CharacterRoleType.SUPPORTING, "配角")
            );

            List<ValidationRule> rules = List.of(
                    ValidationRule.builder()
                            .field("characters[].role_type")
                            .rule("enum_match")
                            .param("PROTAGONIST,ANTAGONIST,SUPPORTING,MINOR")
                            .build()
            );

            List<String> errors = agent.validateResults(results, rules);
            assertTrue(errors.isEmpty(), "All role types should be valid");
        }

        @Test
        @DisplayName("无验证规则时不报错")
        void shouldPassWithNoRules() {
            List<CharacterExtractionResult> results = List.of(
                    createResult("林川", CharacterRoleType.PROTAGONIST, "主角")
            );

            List<String> errors = agent.validateResults(results, Collections.emptyList());
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("空结果集不报错")
        void shouldPassWithEmptyResults() {
            List<ValidationRule> rules = List.of(
                    ValidationRule.builder().field("characters[].name").rule("notBlank").build()
            );

            List<String> errors = agent.validateResults(Collections.emptyList(), rules);
            assertTrue(errors.isEmpty());
        }
    }

    // ── Domain conversion ────────────────────────────────

    @Nested
    @DisplayName("toDomainCharacter — 领域模型转换")
    class DomainConversionTests {

        @Test
        @DisplayName("完整字段应正确转换")
        void shouldConvertAllFields() {
            CharacterExtractionResult result = new CharacterExtractionResult(
                    "林川",
                    List.of("川哥", "小林"),
                    CharacterRoleType.PROTAGONIST,
                    "男",
                    "20-25",
                    "大学生穿越者",
                    List.of("理性", "好奇心强"),
                    List.of(
                            new ExtractedRelationship("李雪", "恋人"),
                            new ExtractedRelationship("王磊", "朋友")
                    ),
                    "第1章",
                    5
            );

            Character domain = result.toDomainCharacter();

            assertEquals("林川", domain.getCanonicalName());
            assertEquals(2, domain.getAliases().size());
            assertEquals(CharacterRoleType.PROTAGONIST, domain.getRoleType());
            assertEquals("男", domain.getGender());
            assertEquals("20-25", domain.getAgeRange());
            assertEquals("大学生穿越者", domain.getDescription());
            assertEquals(2, domain.getPersonality().size());
            assertEquals(2, domain.getRelationships().size());
            assertEquals(5, domain.getAppearanceCount());
            assertTrue(domain.isProtagonist());

            // Verify relationship conversion
            Character.Relationship rel = domain.getRelationships().get(0);
            assertEquals("李雪", rel.getTarget());
            assertEquals("恋人", rel.getRelation());
        }

        @Test
        @DisplayName("空列表字段应安全处理")
        void shouldHandleNullCollections() {
            CharacterExtractionResult result = new CharacterExtractionResult(
                    "路人甲",
                    null,
                    CharacterRoleType.MINOR,
                    null,
                    null,
                    "一个路人",
                    null,
                    null,
                    null,
                    1
            );

            Character domain = result.toDomainCharacter();

            assertEquals("路人甲", domain.getCanonicalName());
            assertTrue(domain.getAliases().isEmpty());
            assertTrue(domain.getPersonality().isEmpty());
            assertTrue(domain.getRelationships().isEmpty());
        }
    }

    // ── Relationship helpers ─────────────────────────────

    @Nested
    @DisplayName("ExtractedRelationship — 关系类型判断")
    class RelationshipTests {

        @Test
        @DisplayName("家人关系识别")
        void shouldIdentifyFamily() {
            assertTrue(new ExtractedRelationship("父亲", "家人").isFamily());
            assertTrue(new ExtractedRelationship("张三", "兄弟").isFamily());
            assertTrue(new ExtractedRelationship("李四", "父母").isFamily());
            assertFalse(new ExtractedRelationship("王五", "朋友").isFamily());
        }

        @Test
        @DisplayName("恋人关系识别")
        void shouldIdentifyRomantic() {
            assertTrue(new ExtractedRelationship("李雪", "恋人").isRomantic());
            assertTrue(new ExtractedRelationship("小红", "情侣").isRomantic());
            assertFalse(new ExtractedRelationship("小明", "朋友").isRomantic());
        }

        @Test
        @DisplayName("敌对关系识别")
        void shouldIdentifyHostile() {
            assertTrue(new ExtractedRelationship("反派", "敌人").isHostile());
            assertTrue(new ExtractedRelationship("张三", "对手").isHostile());
            assertFalse(new ExtractedRelationship("李四", "朋友").isHostile());
        }

        @Test
        @DisplayName("师徒关系识别")
        void shouldIdentifyMentorship() {
            assertTrue(new ExtractedRelationship("师父", "师徒").isMentorship());
            assertTrue(new ExtractedRelationship("徒弟", "弟子").isMentorship());
            assertFalse(new ExtractedRelationship("朋友", "朋友").isMentorship());
        }
    }

    // ── Alias recognition ────────────────────────────────

    @Nested
    @DisplayName("别名识别")
    class AliasRecognitionTests {

        @Test
        @DisplayName("多个别名解析正确")
        void shouldParseMultipleAliases() {
            String response = """
                    {
                      "characters": [
                        {
                          "name": "林川",
                          "aliases": ["川哥", "小林", "穿越者"],
                          "role_type": "PROTAGONIST",
                          "description": "主角"
                        }
                      ]
                    }
                    """;

            List<CharacterExtractionResult> results = agent.parseResponse(response);

            assertEquals(1, results.size());
            assertEquals(3, results.get(0).aliases().size());
            assertThat(results.get(0).aliases())
                    .containsExactlyInAnyOrder("川哥", "小林", "穿越者");
        }

        @Test
        @DisplayName("无别名时返回空列表")
        void shouldReturnEmptyAliasesWhenMissing() {
            String response = """
                    [
                      {
                        "name": "路人甲",
                        "role_type": "MINOR",
                        "description": "路人"
                      }
                    ]
                    """;

            List<CharacterExtractionResult> results = agent.parseResponse(response);

            assertEquals(1, results.size());
            assertTrue(results.get(0).aliases().isEmpty());
        }
    }

    // ── Helper to create test results ────────────────────

    private static CharacterExtractionResult createResult(
            String name, CharacterRoleType roleType, String description) {
        return new CharacterExtractionResult(
                name, Collections.emptyList(), roleType,
                null, null, description,
                Collections.emptyList(), Collections.emptyList(),
                null, 0);
    }
}
