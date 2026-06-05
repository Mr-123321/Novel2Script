package com.novel2script.application.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmarks for {@link ChapterParser}.
 * <p>
 * Simulates 100万+ character novel parsing without JMH dependencies,
 * using wall-clock timing via {@link System#nanoTime()}.
 * </p>
 */
@DisplayName("ChapterParser Benchmark (100万字)")
class ChapterParserBenchmark {

    private final ChapterParser parser = new ChapterParser();

    /**
     * Generate a synthetic novel with the given number of chapters.
     * Each chapter has ~4000 characters of body text.
     */
    private String generateNovel(int chapterCount) {
        StringBuilder sb = new StringBuilder();
        String bodyText = "林川走在宽阔的大道上，两旁是高耸的建筑和熙熙攘攘的人群。"
                + "他抬头看了看天空，蔚蓝的天空中飘着几朵白云。"
                + "突然，一阵风吹过，带来了远处的花香。"
                + "他深吸一口气，感受着这个世界的每一个细节。"
                + "这里的一切都是那么真实，又那么不真实。"
                + "自从穿越到这个世界以来，他已经经历了太多不可思议的事情。"
                + "但是他知道，这仅仅只是开始。\n";

        for (int i = 1; i <= chapterCount; i++) {
            sb.append("第").append(i).append("章 新的征程\n\n");
            // Repeat body text ~28 times per chapter = ~4200 chars
            for (int j = 0; j < 28; j++) {
                sb.append(bodyText);
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // ──── ParseAll benchmarks ────

    @Test
    @DisplayName("10万字 / 25章 parseAll")
    void benchmark100KNovel() {
        String novel = generateNovel(25);
        long chars = novel.length();
        assertTrue(chars >= 100_000,
                "Expected >= 100K chars, got " + chars);

        long start = System.nanoTime();
        List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
        long elapsed = System.nanoTime() - start;

        assertEquals(25, chapters.size());
        System.out.printf("[parseAll] 10万字 / 25章: %,d ms, %,d 字/秒%n",
                elapsed / 1_000_000,
                (long) (chars / (elapsed / 1_000_000_000.0)));
        assertTrue(elapsed < 2_000_000_000L, // 2 seconds max
                "100K chars should parse in < 2s, took " + elapsed / 1_000_000 + "ms");
    }

    @Test
    @DisplayName("50万字 / 125章 parseAll")
    void benchmark500KNovel() {
        String novel = generateNovel(125);
        long chars = novel.length();
        assertTrue(chars >= 500_000,
                "Expected >= 500K chars, got " + chars);

        long start = System.nanoTime();
        List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
        long elapsed = System.nanoTime() - start;

        assertEquals(125, chapters.size());
        System.out.printf("[parseAll] 50万字 / 125章: %,d ms, %,d 字/秒%n",
                elapsed / 1_000_000,
                (long) (chars / (elapsed / 1_000_000_000.0)));
        assertTrue(elapsed < 5_000_000_000L, // 5 seconds max
                "500K chars should parse in < 5s, took " + elapsed / 1_000_000 + "ms");
    }

    @Test
    @DisplayName("100万字 / 250章 parseAll")
    void benchmark1MNovel() {
        String novel = generateNovel(250);
        long chars = novel.length();
        assertTrue(chars >= 1_000_000,
                "Expected >= 1M chars, got " + chars);

        System.gc(); // Hint GC before measurement
        long start = System.nanoTime();
        List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
        long elapsed = System.nanoTime() - start;

        assertEquals(250, chapters.size());
        double charsPerSec = chars / (elapsed / 1_000_000_000.0);
        System.out.printf("[parseAll] 100万字 / 250章: %,d ms, %,d 字/秒%n",
                elapsed / 1_000_000,
                (long) charsPerSec);
        assertTrue(elapsed < 10_000_000_000L, // 10 seconds max
                "1M chars should parse in < 10s, took " + elapsed / 1_000_000 + "ms");
    }

    // ──── Streaming benchmarks ────

    @Test
    @DisplayName("100万字流式解析")
    void benchmark1MNovelStreaming() {
        String novel = generateNovel(250);
        assertTrue(novel.length() >= 1_000_000);

        System.gc();
        long start = System.nanoTime();
        Iterator<ChapterParseResult> it = parser.parse(stream(novel));

        int count = 0;
        while (it.hasNext()) {
            ChapterParseResult ch = it.next();
            assertNotNull(ch.content());
            assertTrue(ch.charCount() > 0);
            count++;
        }
        long elapsed = System.nanoTime() - start;

        assertEquals(250, count);
        System.out.printf("[streaming] 100万字 / 250章: %,d ms%n",
                elapsed / 1_000_000);
        assertTrue(elapsed < 15_000_000_000L, // 15 seconds max
                "1M chars streaming should complete in < 15s, took "
                + elapsed / 1_000_000 + "ms");
    }

    // ──── TOC benchmark ────

    @Test
    @DisplayName("100万字目录解析")
    void benchmarkTOC() {
        String novel = generateNovel(250);
        assertTrue(novel.length() >= 1_000_000);

        long start = System.nanoTime();
        List<ChapterMeta> toc = parser.parseTOC(stream(novel));
        long elapsed = System.nanoTime() - start;

        assertEquals(250, toc.size());
        System.out.printf("[TOC] 100万字 / 250章目录: %,d ms%n",
                elapsed / 1_000_000);
        assertTrue(elapsed < 3_000_000_000L, // 3 seconds max
                "TOC parsing should be fast, took " + elapsed / 1_000_000 + "ms");
    }

    // ──── Memory pressure test ────

    @Test
    @DisplayName("流式解析不应 OOM")
    void testNoOOM() {
        // Generate content that would cause OOM if loaded all at once
        String novel = generateNovel(250);
        long chars = novel.length();
        System.out.printf("Testing with %,d characters (~%.1f MB UTF-8)%n",
                chars, chars * 2.0 / (1024 * 1024)); // Chinese chars ~2 bytes each in UTF-8

        Iterator<ChapterParseResult> it = parser.parse(stream(novel));
        int count = 0;
        long peakContentSize = 0;
        while (it.hasNext()) {
            ChapterParseResult ch = it.next();
            // Each chapter should only hold its own content in memory
            long contentSize = ch.content() != null ? ch.content().length() : 0;
            if (contentSize > peakContentSize) {
                peakContentSize = contentSize;
            }
            assertTrue(contentSize < novel.length() / 2,
                    "Single chapter should not hold entire novel content");
            count++;
        }
        assertEquals(250, count);
        System.out.printf("Peak single-chapter content size: %,d chars%n", peakContentSize);
    }

    // ──── Batched chapter size consistency ────

    @Test
    @DisplayName("多章节字数一致性")
    void testCharsConsistency() {
        String novel = generateNovel(10);
        List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
        assertEquals(10, chapters.size());

        // All chapters should have roughly similar char counts
        int totalChars = chapters.stream().mapToInt(ChapterParseResult::charCount).sum();
        assertEquals(novel.length(), totalChars,
                "Sum of chapter charCounts should equal total input length");
    }
}
