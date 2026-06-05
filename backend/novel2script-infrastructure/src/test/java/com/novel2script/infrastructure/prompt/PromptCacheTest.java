package com.novel2script.infrastructure.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptCache — 响应缓存单元测试")
class PromptCacheTest {

    @Nested
    @DisplayName("1. 基本操作")
    class BasicOperations {

        @Test
        @DisplayName("put + get 应正确存取缓存值")
        void shouldStoreAndRetrieve() {
            PromptCache cache = new PromptCache(60, 100);

            cache.put("What is AI?", "deepseek-chat", 0.7, "AI is...");
            var result = cache.get("What is AI?", "deepseek-chat", 0.7);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("AI is...");
        }

        @Test
        @DisplayName("不同模型名应生成不同缓存 key")
        void shouldHaveDifferentKeysForDifferentModels() {
            PromptCache cache = new PromptCache(60, 100);

            cache.put("prompt", "deepseek-chat", 0.7, "ds-response");
            cache.put("prompt", "claude-sonnet-4-6", 0.7, "claude-response");

            var dsResult = cache.get("prompt", "deepseek-chat", 0.7);
            var clResult = cache.get("prompt", "claude-sonnet-4-6", 0.7);

            assertThat(dsResult).hasValue("ds-response");
            assertThat(clResult).hasValue("claude-response");
            assertThat(dsResult).isNotEqualTo(clResult);
        }

        @Test
        @DisplayName("不同温度应生成不同缓存 key")
        void shouldHaveDifferentKeysForDifferentTemperatures() {
            PromptCache cache = new PromptCache(60, 100);

            cache.put("prompt", "deepseek-chat", 0.3, "cold");
            cache.put("prompt", "deepseek-chat", 0.9, "hot");

            assertThat(cache.get("prompt", "deepseek-chat", 0.3)).hasValue("cold");
            assertThat(cache.get("prompt", "deepseek-chat", 0.9)).hasValue("hot");
        }

        @Test
        @DisplayName("未命中应返回 empty")
        void shouldReturnEmptyOnMiss() {
            PromptCache cache = new PromptCache(60, 100);
            var result = cache.get("never-cached", "any-model", 0.5);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("2. 缓存管理")
    class CacheManagement {

        @Test
        @DisplayName("invalidateAll 应清空所有缓存")
        void shouldInvalidateAll() {
            PromptCache cache = new PromptCache(60, 100);
            cache.put("p1", "m", 0.5, "r1");
            cache.put("p2", "m", 0.5, "r2");

            assertThat(cache.size()).isEqualTo(2);
            cache.invalidateAll();
            assertThat(cache.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("stats 应返回缓存统计信息")
        void shouldReturnStats() {
            PromptCache cache = new PromptCache(60, 100);
            cache.put("p1", "m", 0.5, "r1");
            cache.get("p1", "m", 0.5); // hit
            cache.get("miss", "m", 0.5); // miss

            var stats = cache.stats();
            assertThat(stats.hitCount()).isEqualTo(1);
            assertThat(stats.missCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("构造函数参数应被正确使用")
        void shouldUseConstructorParameters() {
            PromptCache cache = new PromptCache(30, 50);
            assertThat(cache.size()).isEqualTo(0);
        }
    }
}
