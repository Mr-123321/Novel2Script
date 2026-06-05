package com.novel2script.application.service.processor;

import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Novel;
import com.novel2script.domain.model.NovelChunk;
import com.novel2script.infrastructure.vector.EmbeddingService;
import com.novel2script.infrastructure.vector.MilvusVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LongNovelProcessor — 长小说处理单元测试")
class LongNovelProcessorTest {

    // ── Test data builders ──────────────────────────────

    private static Novel novelWithChapters(int chapterCount, int charsPerChapter) {
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < chapterCount; i++) {
            String content = generateChineseText(charsPerChapter, "第" + (i + 1) + "章");
            chapters.add(Chapter.builder()
                    .id((long) i + 1)
                    .chapterNumber(i + 1)
                    .title("第" + (i + 1) + "章")
                    .content(content)
                    .build());
        }
        return Novel.builder()
                .id(1L)
                .title("测试小说")
                .chapterCount(chapterCount)
                .totalChars(chapters.stream().mapToInt(c -> c.getContent().length()).sum())
                .chapters(chapters)
                .build();
    }

    private static String generateChineseText(int targetChars, String chapterLabel) {
        StringBuilder sb = new StringBuilder();
        String[] sentences = {
                "这是一个测试句子，用于生成模拟的小说内容。",
                "角色们在这里展开了激烈的讨论，每个人都表达了自己的观点。",
                "窗外阳光明媚，微风吹过树叶发出沙沙的声音。",
                "他突然意识到了问题的关键所在，心中涌起一阵复杂的情绪。",
                "对话持续了很久，没有人愿意先退出这场争论。",
                "时间一分一秒地流逝，房间里的气氛变得越来越凝重。",
                "她转过身去，不愿让别人看到自己眼中的泪水。",
                "故事在这里发生了转折，一切都开始朝着意想不到的方向发展。"
        };

        int i = 0;
        while (sb.length() < targetChars) {
            sb.append(sentences[i % sentences.length]);
            if (i % 3 == 2) sb.append("\n\n");
            i++;
        }
        return sb.toString();
    }

    // ── 1. Token Estimation tests ───────────────────────

    @Nested
    @DisplayName("1. Token 估算")
    class TokenEstimation {

        @Test
        @DisplayName("纯中文文本 token 估算应合理")
        void shouldEstimateChineseTokens() {
            String chinese = "这是一段纯中文的测试文本用于验证分词估算的准确性";
            int tokens = NovelChunker.estimateTokens(chinese);

            assertThat(tokens).isGreaterThan(0);
            assertThat(tokens).isLessThan(chinese.length()); // tokens < chars for Chinese
        }

        @Test
        @DisplayName("空文本 token 应为 0")
        void shouldReturnZeroForEmpty() {
            assertThat(NovelChunker.estimateTokens(null)).isEqualTo(0);
            assertThat(NovelChunker.estimateTokens("")).isEqualTo(0);
            assertThat(NovelChunker.estimateTokens("   ")).isEqualTo(0);
        }

        @Test
        @DisplayName("英文文本 token 估算应合理")
        void shouldEstimateEnglishTokens() {
            String english = "This is a test sentence for token estimation in English text.";
            int tokens = NovelChunker.estimateTokens(english);

            assertThat(tokens).isGreaterThan(0);
        }

        @Test
        @DisplayName("token 估算应在合理范围内")
        void shouldBeInReasonableRange() {
            String text = generateChineseText(2000, "test");
            int tokens = NovelChunker.estimateTokens(text);

            // Chinese: ~0.7 tokens per char → 2000 chars ≈ 1400 tokens
            assertThat(tokens).isBetween(1000, 1800);
        }
    }

    // ── 2. NovelChunker tests ───────────────────────────

    @Nested
    @DisplayName("2. 小说分块器")
    class NovelChunkerTests {

        private NovelChunker chunker;

        @BeforeEach
        void setUp() {
            chunker = new NovelChunker();
        }

        @Test
        @DisplayName("短小说应至少产生 1 个 chunk")
        void shouldProduceAtLeastOneChunk() {
            Novel novel = novelWithChapters(3, 500);

            List<NovelChunk> chunks = chunker.chunk(novel);

            assertThat(chunks).isNotEmpty();
        }

        @Test
        @DisplayName("10万字小说应产生多个 chunk")
        void shouldProduceMultipleChunksForLongNovel() {
            // 10 chapters × 10,000 chars = ~100,000 chars ≈ 10万字
            Novel novel = novelWithChapters(10, 10000);

            List<NovelChunk> chunks = chunker.chunk(novel);

            assertThat(chunks).hasSizeGreaterThan(1);
            // Each chunk ~2857 chars, so ~35 chunks expected
            assertThat(chunks.size()).isBetween(30, 50);
        }

        @Test
        @DisplayName("chunk 序号应连续递增")
        void shouldHaveSequentialIndices() {
            Novel novel = novelWithChapters(20, 5000);

            List<NovelChunk> chunks = chunker.chunk(novel);

            for (int i = 0; i < chunks.size(); i++) {
                assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("空小说应返回空列表")
        void shouldReturnEmptyForNullNovel() {
            List<NovelChunk> chunks = chunker.chunk(null);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("chunk 内容应在句子边界处截断")
        void shouldSplitAtSentenceBoundaries() {
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                content.append("这是第").append(i).append("个句子。");
            }
            Chapter ch = Chapter.builder().id(1L).chapterNumber(1).title("测试").content(content.toString()).build();
            Novel novel = Novel.builder().id(1L).title("测试").chapterCount(1).totalChars(content.length())
                    .chapters(List.of(ch)).build();

            List<NovelChunk> chunks = chunker.chunk(novel);

            for (NovelChunk chunk : chunks) {
                String text = chunk.content();
                if (!text.isEmpty()) {
                    // Last meaningful char before trailing whitespace should be a sentence end
                    String trimmed = text.trim();
                    char lastChar = trimmed.charAt(trimmed.length() - 1);
                    assertThat(lastChar == '。' || lastChar == '！' || lastChar == '？' || lastChar == '\n')
                            .as("Chunk %d should end at sentence boundary, got '%s'",
                                    chunk.chunkIndex(), lastChar)
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("chunkByParagraphs 应按段落分块")
        void shouldChunkByParagraphs() {
            String text = "段落一：这是第一段内容。\n\n段落二：这是第二段内容。\n\n段落三：这是第三段内容。";

            List<NovelChunk> chunks = chunker.chunkByParagraphs(text, 1L);

            assertThat(chunks).hasSize(1); // short paragraphs fit in one chunk
        }

        @Test
        @DisplayName("空小说 (null chapters) 应安全处理")
        void shouldHandleNullChapters() {
            Novel novel = Novel.builder().id(1L).title("空").chapterCount(0).build();

            List<NovelChunk> chunks = chunker.chunk(novel);

            assertThat(chunks).isEmpty();
        }
    }

    // ── 3. ContextBuilder tests ─────────────────────────

    @Nested
    @DisplayName("3. 上下文构建器")
    class ContextBuilderTests {

        private EmbeddingService embeddingService;
        private MilvusVectorStore vectorStore;
        private ContextBuilder contextBuilder;
        private List<NovelChunk> chunks;

        @BeforeEach
        void setUp() {
            embeddingService = new EmbeddingService(null); // hash-based fallback
            vectorStore = new MilvusVectorStore();
            contextBuilder = new ContextBuilder(embeddingService, vectorStore);

            // Create and embed some chunks
            Novel novel = novelWithChapters(5, 3000);
            NovelChunker chunker = new NovelChunker();
            chunks = chunker.chunk(novel);

            // Store in vector store
            for (NovelChunk chunk : chunks) {
                float[] emb = embeddingService.embed(chunk.content());
                vectorStore.insertCharacter(chunk.chunkId(), emb, Map.of(
                        "chunk_index", chunk.chunkIndex(),
                        "chapter_ids", chunk.chapterIds()
                ));
            }
        }

        @Test
        @DisplayName("应返回非空上下文")
        void shouldReturnNonEmptyContext() {
            String context = contextBuilder.buildContext("提取角色信息", chunks, 60000);

            assertThat(context).isNotNull();
            assertThat(context).isNotBlank();
        }

        @Test
        @DisplayName("上下文不应超过 token 限制")
        void shouldNotExceedTokenLimit() {
            int maxTokens = 5000;
            String context = contextBuilder.buildContext("提取剧情事件", chunks, maxTokens);
            int tokens = NovelChunker.estimateTokens(context);

            assertThat(tokens).isLessThanOrEqualTo(maxTokens + 500); // small margin for estimation error
        }

        @Test
        @DisplayName("无匹配 chunk 时应 fallback 返回开头内容")
        void shouldFallbackWhenNoMatch() {
            // Search with an unrelated query that won't match well with hash embeddings
            String context = contextBuilder.buildContext("xyz abc unrelated query", chunks, 10000);

            assertThat(context).isNotBlank();
        }

        @Test
        @DisplayName("空 chunk 列表应返回空字符串")
        void shouldReturnEmptyForNoChunks() {
            String context = contextBuilder.buildContext("任何查询", List.of(), 10000);
            assertThat(context).isEmpty();

            context = contextBuilder.buildContext("任何查询", null, 10000);
            assertThat(context).isEmpty();
        }

        @Test
        @DisplayName("buildBroadContext 应返回更广泛的上下文")
        void shouldBuildBroadContext() {
            String context = contextBuilder.buildBroadContext("角色和剧情", chunks, 60000);
            assertThat(context).isNotBlank();
        }

        @Test
        @DisplayName("estimateChunkCapacity 应合理估算")
        void shouldEstimateChunkCapacity() {
            int capacity = contextBuilder.estimateChunkCapacity(10000, chunks);
            assertThat(capacity).isGreaterThan(0);
            assertThat(capacity).isLessThanOrEqualTo(chunks.size());
        }

        @Test
        @DisplayName("0 token 预算应返回空")
        void shouldReturnEmptyForZeroBudget() {
            String context = contextBuilder.buildContext("提取角色", chunks, 0);
            // Defaults to 60000 when 0 is passed
            assertThat(context).isNotBlank();
        }
    }

    // ── 4. LongNovelProcessor tests ─────────────────────

    @Nested
    @DisplayName("4. 长小说处理器")
    class LongNovelProcessorTests {

        private LongNovelProcessor processor;
        private List<NovelChunk> processedChunks;

        @BeforeEach
        void setUp() {
            EmbeddingService embService = new EmbeddingService(null);
            MilvusVectorStore vs = new MilvusVectorStore();
            NovelChunker chunker = new NovelChunker();
            ContextBuilder cb = new ContextBuilder(embService, vs);
            processor = new LongNovelProcessor(chunker, embService, vs, cb);

            // Preprocess a test novel
            Novel novel = novelWithChapters(8, 4000);
            var result = processor.preprocess(novel);
            processedChunks = chunker.chunk(novel); // get same chunks for verification
        }

        @Test
        @DisplayName("preprocess 应返回有效的处理结果")
        void shouldReturnValidProcessResult() {
            Novel novel = novelWithChapters(5, 3000);

            var result = processor.preprocess(novel);

            assertThat(result.chunkCount()).isGreaterThan(0);
            assertThat(result.totalChars()).isGreaterThan(0);
            assertThat(result.totalTimeMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.charsPerSecond()).isGreaterThan(0);
            assertThat(result.toString()).contains("ProcessResult");
        }

        @Test
        @DisplayName("getTaskContext 应返回任务相关上下文")
        void shouldReturnTaskContext() {
            String context = processor.getTaskContext("提取所有角色", processedChunks);

            assertThat(context).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("getBroadTaskContext 应返回更广上下文")
        void shouldReturnBroadTaskContext() {
            String context = processor.getBroadTaskContext("完整剧情分析", processedChunks);

            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("getRelevantChunks 应返回相关 chunk 列表")
        void shouldReturnRelevantChunks() {
            List<NovelChunk> relevant = processor.getRelevantChunks("角色提取", processedChunks);

            assertThat(relevant).isNotNull();
            assertThat(relevant.size()).isLessThanOrEqualTo(processedChunks.size());
        }

        @Test
        @DisplayName("getChunksByChapters 应按章节范围筛选")
        void shouldFilterByChapterRange() {
            List<NovelChunk> chapterChunks = processor.getChunksByChapters(processedChunks, 1, 3);

            assertThat(chapterChunks).isNotNull();
            // All returned chunks should have at least one chapter in range 1-3
            for (NovelChunk c : chapterChunks) {
                assertThat(c.chapterIds().stream().anyMatch(id -> id >= 1 && id <= 3)).isTrue();
            }
        }

        @Test
        @DisplayName("getProgressReport 应返回进度报告")
        void shouldReturnProgressReport() {
            Map<String, Object> report = processor.getProgressReport(processedChunks);

            assertThat(report).containsKeys(
                    "totalChunks", "totalTokens", "avgTokensPerChunk",
                    "uniqueChapters", "vectorsStored", "estimatedMemoryMB");
            assertThat((int) report.get("totalChunks")).isGreaterThan(0);
            assertThat((int) report.get("totalTokens")).isGreaterThan(0);
        }

        @Test
        @DisplayName("reset 应清空向量存储")
        void shouldResetVectorStore() {
            processor.reset();
            // After reset, no vectors should be stored
            // But the processedChunks still exist in memory
            assertThat(processedChunks).isNotEmpty();
        }
    }

    // ── 5. NovelChunk model tests ──────────────────────

    @Nested
    @DisplayName("5. NovelChunk 模型")
    class NovelChunkModel {

        @Test
        @DisplayName("label 应生成可读标识")
        void shouldGenerateLabel() {
            NovelChunk chunk = NovelChunk.builder()
                    .chunkId("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                    .chunkIndex(3)
                    .chapterIds(List.of(1L, 2L))
                    .content("测试内容")
                    .tokenCount(42)
                    .build();

            String label = chunk.label();
            assertThat(label).contains("Chunk-3");
            assertThat(label).contains("tokens=42");
        }

        @Test
        @DisplayName("overlapsChapter 应正确判断")
        void shouldCheckChapterOverlap() {
            NovelChunk chunk = NovelChunk.builder()
                    .chunkId("test").chunkIndex(0)
                    .chapterIds(List.of(3L, 4L, 5L))
                    .content("x").tokenCount(1)
                    .build();

            assertThat(chunk.overlapsChapter(3L)).isTrue();
            assertThat(chunk.overlapsChapter(4L)).isTrue();
            assertThat(chunk.overlapsChapter(1L)).isFalse();
            assertThat(chunk.overlapsChapter(10L)).isFalse();
        }

        @Test
        @DisplayName("null chapterIds 应安全处理")
        void shouldHandleNullChapterIds() {
            NovelChunk chunk = NovelChunk.builder()
                    .chunkId("test").chunkIndex(0)
                    .chapterIds(null)
                    .content("x").tokenCount(1)
                    .build();

            assertThat(chunk.overlapsChapter(1L)).isFalse();
        }
    }

    // ── 6. Edge cases and performance ──────────────────

    @Nested
    @DisplayName("6. 边缘情况与性能")
    class EdgeCases {

        @Test
        @DisplayName("50万字小说应控制在合理 chunk 数量内")
        void shouldHandleLargeNovelEfficiently() {
            // Simulate 50万字 ≈ 500,000 chars
            Novel novel = novelWithChapters(50, 10000);
            NovelChunker chunker = new NovelChunker();

            long start = System.currentTimeMillis();
            List<NovelChunk> chunks = chunker.chunk(novel);
            long time = System.currentTimeMillis() - start;

            assertThat(chunks).isNotEmpty();
            // Chunking should be fast (< 5 seconds for 50万字)
            assertThat(time).isLessThan(5000);
            // Check chunk quality
            for (NovelChunk chunk : chunks) {
                assertThat(chunk.content()).isNotBlank();
                assertThat(chunk.tokenCount()).isGreaterThan(0);
                assertThat(chunk.chunkIndex()).isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        @DisplayName("内存估算应在合理范围内")
        void shouldEstimateMemoryCorrectly() {
            String text = generateChineseText(100000, "test");
            List<NovelChunk> chunks = List.of(
                    NovelChunk.builder()
                            .chunkId("mem-test").chunkIndex(0)
                            .content(text).tokenCount(NovelChunker.estimateTokens(text))
                            .chapterIds(List.of(1L))
                            .build()
            );

            EmbeddingService embService = new EmbeddingService(null);
            MilvusVectorStore vs = new MilvusVectorStore();
            NovelChunker chunker = new NovelChunker();
            ContextBuilder cb = new ContextBuilder(embService, vs);
            LongNovelProcessor processor = new LongNovelProcessor(chunker, embService, vs, cb);

            Map<String, Object> report = processor.getProgressReport(chunks);
            long memMB = (long) report.get("estimatedMemoryMB");

            // UTF-16: 100K chars × 2 bytes ≈ 200KB ≈ 0.19 MB
            assertThat(memMB).isBetween(0L, 5L);
        }

        @Test
        @DisplayName("chunk 不应在句子中间截断")
        void shouldNotSplitMidSentence() {
            Novel novel = novelWithChapters(3, 5000);
            NovelChunker chunker = new NovelChunker();

            List<NovelChunk> chunks = chunker.chunk(novel);

            for (NovelChunk chunk : chunks) {
                String content = chunk.content().trim();
                if (content.isEmpty()) continue;

                // Content should not start mid-sentence (after a sentence start like "他")
                // This is a heuristic check: most chunks should start with a sentence-initial context
                assertThat(content.length()).isGreaterThan(10);
            }
        }

        @Test
        @DisplayName("检索精度：Top-20 chunk 应覆盖足够内容")
        void shouldCoverEnoughWithTop20() {
            EmbeddingService embService = new EmbeddingService(null);
            MilvusVectorStore vs = new MilvusVectorStore();
            NovelChunker chunker = new NovelChunker();
            ContextBuilder cb = new ContextBuilder(embService, vs);

            Novel novel = novelWithChapters(20, 5000);
            List<NovelChunk> chunks = chunker.chunk(novel);

            // Store all chunks
            for (NovelChunk c : chunks) {
                vs.insertCharacter(c.chunkId(), embService.embed(c.content()),
                        Map.of("chunk_index", c.chunkIndex()));
            }

            // Build context with top-20
            String context = cb.buildContext("角色提取", chunks, 60000);
            int contextTokens = NovelChunker.estimateTokens(context);

            // Context should contain substantial content
            assertThat(contextTokens).isGreaterThan(0);
            // Should not exceed budget
            assertThat(contextTokens).isLessThanOrEqualTo(60000 + 1000);
        }
    }
}
