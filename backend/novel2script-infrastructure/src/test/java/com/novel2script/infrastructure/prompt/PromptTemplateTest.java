package com.novel2script.infrastructure.prompt;

import com.novel2script.domain.prompt.FewShot;
import com.novel2script.domain.prompt.JsonSchema;
import com.novel2script.domain.prompt.ValidationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptTemplate — 模板渲染单元测试")
class PromptTemplateTest {

    @Nested
    @DisplayName("1. 基本渲染")
    class BasicRendering {

        @Test
        @DisplayName("render 应正确渲染 Mustache 模板变量")
        void shouldRenderMustacheVariables() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("你是一个助手。")
                    .userTemplate("请回答：{{question}}")
                    .build();

            var prompt = tpl.render(Map.of("question", "什么是AI?"));

            assertThat(prompt).isNotNull();
            assertThat(prompt.getInstructions()).hasSize(2); // system + user
            String userText = prompt.getInstructions().get(1).getText();
            assertThat(userText).isEqualTo("请回答：什么是AI?");
        }

        @Test
        @DisplayName("render 应处理列表迭代语法")
        void shouldRenderListIteration() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("助手")
                    .userTemplate("{{#items}}- {{name}}: {{value}}\n{{/items}}")
                    .build();

            List<Map<String, Object>> items = List.of(
                    Map.of("name", "a", "value", "1"),
                    Map.of("name", "b", "value", "2"));

            String result = tpl.renderUserTemplate(Map.of("items", items));
            assertThat(result).contains("- a: 1", "- b: 2");
        }

        @Test
        @DisplayName("render 应处理条件渲染")
        void shouldRenderConditionals() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("助手")
                    .userTemplate("{{#show}}可见内容{{/show}}{{^show}}默认内容{{/show}}")
                    .build();

            String withShow = tpl.renderUserTemplate(Map.of("show", true));
            assertThat(withShow).contains("可见内容");

            String withoutShow = tpl.renderUserTemplate(Map.of());
            assertThat(withoutShow).contains("默认内容");
        }
    }

    @Nested
    @DisplayName("2. Few-shot 注入")
    class FewShotInjection {

        @Test
        @DisplayName("buildSystemWithFewShots 应将 few-shot 示例追加到 system prompt")
        void shouldAppendFewShotsToSystem() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("你是一个助手。")
                    .fewShots(List.of(
                            FewShot.builder().input("1+1?").output("2").build(),
                            FewShot.builder().input("2+2?").output("4").build()))
                    .build();

            String system = tpl.buildSystemWithFewShots();
            assertThat(system).contains("你是一个助手。");
            assertThat(system).contains("示例 1");
            assertThat(system).contains("示例 2");
            assertThat(system).contains("1+1?");
            assertThat(system).contains("2+2?");
            assertThat(system).contains("请按照以上示例的格式输出");
        }

        @Test
        @DisplayName("无 few-shot 示例时不应追加示例区块")
        void shouldNotAppendWhenNoFewShots() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("你是一个助手。")
                    .fewShots(List.of())
                    .build();

            String system = tpl.buildSystemWithFewShots();
            assertThat(system).doesNotContain("示例");
            assertThat(system).isEqualTo("你是一个助手。");
        }
    }

    @Nested
    @DisplayName("3. JSON Schema 验证")
    class JsonSchemaHandling {

        @Test
        @DisplayName("有 outputSchema 时应追加格式说明")
        void shouldAppendSchemaInstruction() {
            JsonSchema schema = JsonSchema.builder()
                    .type("object")
                    .build();

            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("助手")
                    .outputSchema(schema)
                    .build();

            String system = tpl.buildSystemWithFewShots();
            assertThat(system).contains("JSON");
        }

        @Test
        @DisplayName("无 outputSchema 时不应追加格式说明")
        void shouldNotAppendWhenNoSchema() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("助手")
                    .build();

            String system = tpl.buildSystemWithFewShots();
            assertThat(system).doesNotContain("JSON");
        }
    }

    @Nested
    @DisplayName("4. Builder 模式")
    class BuilderPattern {

        @Test
        @DisplayName("builder 应正确设置所有字段")
        void shouldBuildAllFields() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("character-extraction")
                    .version("2.0")
                    .description("提取角色")
                    .recommendedModel("claude-sonnet-4-6")
                    .temperature(0.3)
                    .maxTokens(4096)
                    .system("你是角色分析师。")
                    .userTemplate("{{#chapters}}{{content}}{{/chapters}}")
                    .fewShots(List.of(FewShot.builder().input("in").output("out").build()))
                    .validationRules(List.of(ValidationRule.builder().field("name").rule("notBlank").build()))
                    .maxRetries(3)
                    .build();

            assertThat(tpl.getName()).isEqualTo("character-extraction");
            assertThat(tpl.getVersion()).isEqualTo("2.0");
            assertThat(tpl.getDescription()).isEqualTo("提取角色");
            assertThat(tpl.getRecommendedModel()).isEqualTo("claude-sonnet-4-6");
            assertThat(tpl.getTemperature()).isEqualTo(0.3);
            assertThat(tpl.getMaxTokens()).isEqualTo(4096);
            assertThat(tpl.getSystem()).isEqualTo("你是角色分析师。");
            assertThat(tpl.getFewShots()).hasSize(1);
            assertThat(tpl.getValidationRules()).hasSize(1);
            assertThat(tpl.getMaxRetries()).isEqualTo(3);
        }

        @Test
        @DisplayName("默认值应正确")
        void shouldHaveCorrectDefaults() {
            PromptTemplate tpl = PromptTemplate.builder()
                    .name("test")
                    .version("1.0")
                    .system("s")
                    .userTemplate("u")
                    .build();

            assertThat(tpl.getRecommendedModel()).isEqualTo("deepseek-chat");
            assertThat(tpl.getTemperature()).isEqualTo(0.7);
            assertThat(tpl.getMaxTokens()).isEqualTo(4096);
            assertThat(tpl.getMaxRetries()).isEqualTo(3);
            assertThat(tpl.getFewShots()).isEmpty();
            assertThat(tpl.getValidationRules()).isEmpty();
        }
    }
}
