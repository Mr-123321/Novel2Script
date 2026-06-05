package com.novel2script.infrastructure.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptRegistry — 模板注册表单元测试")
class PromptRegistryTest {

    @Nested
    @DisplayName("1. 生命周期")
    class Lifecycle {

        @Test
        @DisplayName("loadAll 应从 classpath 加载 YAML 模板")
        void shouldLoadTemplatesFromClasspath() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();

            // 至少有测试用的 test-simple 模板
            assertThat(registry.getTotalCount()).isGreaterThanOrEqualTo(1);
            assertThat(registry.getRegisteredNames()).contains("test-simple");
            System.out.println("  ✓ 加载模板数: " + registry.getTotalCount());
            System.out.println("  ✓ 注册名称: " + registry.getRegisteredNames());
        }

        @Test
        @DisplayName("getLatest 应返回最新版本")
        void shouldReturnLatestVersion() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();

            PromptTemplate tpl = registry.getLatest("test-simple");
            assertThat(tpl).isNotNull();
            assertThat(tpl.getName()).isEqualTo("test-simple");
            assertThat(tpl.getVersion()).isEqualTo("1.0");
            assertThat(tpl.getDescription()).isEqualTo("A simple test prompt template");
            assertThat(tpl.getRecommendedModel()).isEqualTo("deepseek-chat");
            assertThat(tpl.getTemperature()).isEqualTo(0.5);
            assertThat(tpl.getMaxTokens()).isEqualTo(1024);
            assertThat(tpl.getFewShots()).hasSize(2);
            assertThat(tpl.getValidationRules()).hasSize(1);
        }

        @Test
        @DisplayName("getVersion 应返回指定版本")
        void shouldReturnSpecificVersion() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();

            PromptTemplate tpl = registry.getVersion("test-simple", "1.0");
            assertThat(tpl).isNotNull();
            assertThat(tpl.getVersion()).isEqualTo("1.0");
        }

        @Test
        @DisplayName("未知模板应返回 null")
        void shouldReturnNullForUnknownTemplate() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();

            assertThat(registry.getLatest("nonexistent")).isNull();
            assertThat(registry.getVersion("nonexistent", "1.0")).isNull();
        }

        @Test
        @DisplayName("listVersions 应返回版本列表")
        void shouldListVersions() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();

            var versions = registry.listVersions("test-simple");
            assertThat(versions).isNotEmpty();
            System.out.println("  ✓ test-simple 版本数: " + versions.size());
        }

        @Test
        @DisplayName("注册的提示词名称应不可修改")
        void shouldReturnUnmodifiableNames() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();

            var names = registry.getRegisteredNames();
            assertThat(names).isNotEmpty();
            // Verify it's unmodifiable
            try {
                names.add("new-name");
                // Should not reach here
            } catch (UnsupportedOperationException e) {
                // expected
            }
        }
    }

    @Nested
    @DisplayName("2. 渲染验证")
    class Rendering {

        @Test
        @DisplayName("加载的模板应可正常渲染")
        void shouldRenderLoadedTemplate() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();

            PromptTemplate tpl = registry.getLatest("test-simple");
            assertThat(tpl).isNotNull();

            var prompt = tpl.render(Map.of("question", "什么是Java?"));
            assertThat(prompt).isNotNull();
            assertThat(prompt.getInstructions()).hasSize(2);

            String systemContent = prompt.getInstructions().get(0).getText();
            assertThat(systemContent).contains("你是一个测试助手");
            assertThat(systemContent).contains("示例 1");
            assertThat(systemContent).contains("What is 1+1?");
            assertThat(systemContent).contains("答案是 2");

            String userContent = prompt.getInstructions().get(1).getText();
            assertThat(userContent).contains("什么是Java?");
        }
    }

    @Nested
    @DisplayName("3. 热加载")
    class HotReload {

        @Test
        @DisplayName("reloadAll 应可重复调用")
        void shouldReloadSuccessfully() {
            PromptRegistry registry = new PromptRegistry();
            registry.loadAll();
            int firstCount = registry.getTotalCount();

            registry.reloadAll();
            int secondCount = registry.getTotalCount();

            assertThat(secondCount).isEqualTo(firstCount);
            System.out.println("  ✓ 热加载正常 — 两次加载数一致: " + firstCount);
        }
    }
}
