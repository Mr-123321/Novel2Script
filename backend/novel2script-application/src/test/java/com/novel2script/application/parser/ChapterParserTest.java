package com.novel2script.application.parser;

import com.novel2script.common.enums.ChapterType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChapterParser}.
 */
@DisplayName("ChapterParser")
class ChapterParserTest {

    private final ChapterParser parser = new ChapterParser();

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // ──── Basic chapter formats ────

    @Nested
    @DisplayName("中文数字章节格式")
    class ChineseNumeralChapters {

        @Test
        @DisplayName("第X章格式")
        void testChineseDigitChapters() {
            String novel = """
                    第1章 穿越
                    林川睁开眼睛，发现自己躺在一张陌生的床上。
                    他环顾四周，房间里堆满了杂物。

                    第2章 觉醒
                    一股热流从丹田涌出，林川猛地坐起身来。
                    他感觉到体内有一股前所未有的力量。

                    第3章 离开
                    林川收拾好行囊，决定踏上旅途。
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(3, chapters.size());
            assertEquals("第1章 穿越", chapters.get(0).title());
            assertEquals("第2章 觉醒", chapters.get(1).title());
            assertEquals("第3章 离开", chapters.get(2).title());
            assertTrue(chapters.get(0).content().contains("林川睁开眼睛"));
            assertEquals(ChapterType.NORMAL, chapters.get(0).type());
        }

        @Test
        @DisplayName("第一章格式（中文大写数字）")
        void testChineseFormalChapters() {
            String novel = """
                    第一章 序言
                    这是故事的开始。

                    第二章 初遇
                    他们在雨中相遇。

                    第三章 离别
                    故事总有结局。
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(3, chapters.size());
            assertEquals("第一章 序言", chapters.get(0).title());
            assertEquals("第二章 初遇", chapters.get(1).title());
        }
    }

    @Nested
    @DisplayName("英文章节格式")
    class EnglishChapters {

        @Test
        @DisplayName("Chapter X格式")
        void testEnglishDigitChapters() {
            String novel = """
                    Chapter 1 The Beginning
                    It was a dark and stormy night.

                    Chapter 2 The Journey
                    They set out at dawn.

                    Chapter 3 The Return
                    Home at last.
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(3, chapters.size());
            assertTrue(chapters.get(0).title().contains("Chapter 1"));
            assertTrue(chapters.get(1).title().contains("Chapter 2"));
        }

        @Test
        @DisplayName("CHAPTER I格式（罗马数字）")
        void testRomanNumeralChapters() {
            String novel = """
                    CHAPTER I
                    In the beginning, there was darkness.

                    CHAPTER II
                    And then there was light.

                    CHAPTER III
                    The world took shape.
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertTrue(chapters.size() >= 3);
        }
    }

    // ──── Special chapter types ────

    @Nested
    @DisplayName("特殊章节类型")
    class SpecialChapterTypes {

        @Test
        @DisplayName("序章识别")
        void testPrologue() {
            String novel = """
                    序章 一切的开始
                    在很久很久以前……

                    第一章 穿越
                    林川睁开了眼睛。
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertTrue(chapters.size() >= 2);
            assertEquals(ChapterType.PROLOGUE, chapters.get(0).type());
        }

        @Test
        @DisplayName("楔子识别")
        void testWedgeChapter() {
            String novel = """
                    楔子
                    天地初开，混沌未明。

                    第一章 觉醒
                    少年从梦中醒来。
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertTrue(chapters.size() >= 2);
            assertEquals(ChapterType.PROLOGUE, chapters.get(0).type());
        }

        @Test
        @DisplayName("尾声识别")
        void testEpilogue() {
            String novel = """
                    第十章 决战
                    最后一战打响了。

                    尾声
                    多年以后，这里已经恢复了和平。
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertTrue(chapters.size() >= 2);
            ChapterParseResult last = chapters.get(chapters.size() - 1);
            assertEquals(ChapterType.EPILOGUE, last.type());
        }

        @Test
        @DisplayName("番外识别")
        void testInterlude() {
            String novel = """
                    第三章 结局
                    故事到此结束。

                    番外 她不知道的事
                    在另一个视角里，事情是这样的……
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertTrue(chapters.size() >= 2);
            ChapterParseResult last = chapters.get(chapters.size() - 1);
            assertEquals(ChapterType.INTERLUDE, last.type());
        }
    }

    // ──── Other formats ────

    @Nested
    @DisplayName("其他格式")
    class OtherFormats {

        @Test
        @DisplayName("第X回格式")
        void testHuiFormat() {
            String novel = """
                    第一回 灵根初现
                    话说天地之间……

                    第二回 踏上仙途
                    少年手持宝剑……
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(2, chapters.size());
        }

        @Test
        @DisplayName("混合格式：卷+章")
        void testVolumeChapterFormat() {
            String novel = """
                    第一卷 凡尘
                    第一章 少年
                    一个普通的清晨……

                    第二章 离别
                    少年踏上了旅途。

                    第二卷 仙界
                    第一章 飞升
                    金光闪过，少年消失在原地。
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            // Should parse all chapter-level headers
            assertTrue(chapters.size() >= 4,
                    "Expected at least 4 chapters, got " + chapters.size());
        }

        @Test
        @DisplayName("前序内容作为第一章")
        void testLeadingContent() {
            String novel = """
                    作者：佚名
                    简介：一个平凡少年的修仙之路。

                    第一章 觉醒
                    少年睁开眼睛……
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertTrue(chapters.size() >= 2,
                    "Leading content should be its own chapter or preamble");
        }
    }

    // ──── Streaming / Iterator tests ────

    @Nested
    @DisplayName("流式解析")
    class StreamingTests {

        @Test
        @DisplayName("Iterator可以惰性读取")
        void testLazyIteration() {
            String novel = "第一章 开始\n内容\n第二章 继续\n更多内容\n";

            Iterator<ChapterParseResult> it = parser.parse(stream(novel));

            assertTrue(it.hasNext());
            ChapterParseResult ch1 = it.next();
            assertEquals("第一章 开始", ch1.title());

            assertTrue(it.hasNext());
            ChapterParseResult ch2 = it.next();
            assertEquals("第二章 继续", ch2.title());

            assertFalse(it.hasNext());
        }

        @Test
        @DisplayName("单章小说")
        void testSingleChapter() {
            String novel = "第一章 唯一\n这是唯一的一章。\n";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(1, chapters.size());
        }

        @Test
        @DisplayName("无章节标题的纯文本")
        void testNoHeaders() {
            String novel = "这是一段没有章节标题的内容。\n一直写下去。\n没有明确的边界。\n";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(1, chapters.size(),
                    "Content without headers should be treated as a single chapter");
        }
    }

    // ──── TOC parsing ────

    @Nested
    @DisplayName("目录解析")
    class TOCTests {

        @Test
        @DisplayName("parseTOC返回目录元数据")
        void testTOCParsing() {
            String novel = """
                    序章
                    前言内容……

                    第一章 穿越
                    第一章正文……

                    第二章 觉醒
                    第二章正文……
                    """;

            List<ChapterMeta> toc = parser.parseTOC(stream(novel));
            assertEquals(3, toc.size());
            assertEquals("序章", toc.get(0).title());
            assertEquals(ChapterType.PROLOGUE, toc.get(0).type());
            assertEquals("第一章 穿越", toc.get(1).title());
        }
    }

    // ──── Offset tracking ────

    @Nested
    @DisplayName("偏移量追踪")
    class OffsetTests {

        @Test
        @DisplayName("章节偏移量正确")
        void testOffsets() {
            String novel = "第一章 开始\n正文第一行。\n正文第二行。\n";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(1, chapters.size());
            ChapterParseResult ch = chapters.get(0);

            assertEquals(0, ch.startOffset(), "First chapter should start at offset 0");
            assertTrue(ch.endOffset() > 0, "End offset should be positive");
            assertTrue(ch.endOffset() > ch.startOffset(),
                    "End offset should be greater than start offset");
        }

        @Test
        @DisplayName("多章节偏移量递增")
        void testMultiChapterOffsets() {
            String novel = "第一章 一\nA\n第二章 二\nBB\n";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));

            for (int i = 0; i < chapters.size(); i++) {
                ChapterParseResult ch = chapters.get(i);
                assertTrue(ch.endOffset() > ch.startOffset(),
                        "Chapter " + (i + 1) + " offsets invalid");
            }

            // Each subsequent chapter should start where the previous ended
            for (int i = 1; i < chapters.size(); i++) {
                assertEquals(chapters.get(i - 1).endOffset(), chapters.get(i).startOffset(),
                        "Chapter " + (i + 1) + " should start where chapter "
                        + i + " ended");
            }
        }
    }

    // ──── Character count ────

    @Nested
    @DisplayName("字数统计")
    class CharCountTests {

        @Test
        @DisplayName("charCount反映正文长度")
        void testCharCount() {
            String novel = "第一章\n12345\n";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(1, chapters.size());

            // "第一章\n12345\n" = 10 chars (including newlines)
            assertTrue(chapters.get(0).charCount() > 0);
        }

        @Test
        @DisplayName("空章节字数正确")
        void testEmptyChapterCharCount() {
            String novel = "第一章 空\n\n第二章 非空\n有内容\n";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(2, chapters.size());
            // Chapter 1: "第一章 空\n\n" = header + empty line
            assertTrue(chapters.get(0).charCount() >= 0);
            // Chapter 2: "第二章 非空\n有内容\n" 
            assertTrue(chapters.get(1).charCount() > chapters.get(0).charCount());
        }
    }

    // ──── Edge cases ────

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空内容")
        void testEmptyContent() {
            String novel = "";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertTrue(chapters.isEmpty());
        }

        @Test
        @DisplayName("仅空白行")
        void testWhitespaceOnly() {
            String novel = "\n\n\n";

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            // No real content → no chapters or 1 empty chapter
            assertTrue(chapters.size() <= 1);
        }

        @Test
        @DisplayName("章节标题重复出现")
        void testDuplicateChapterTitle() {
            String novel = """
                    第一章 开始
                    内容A

                    第一章 开始
                    内容B（不同的内容）
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(2, chapters.size(),
                    "Duplicate titles should still produce separate chapters");
            assertTrue(chapters.get(0).content().contains("内容A"));
            assertTrue(chapters.get(1).content().contains("内容B"));
        }

        @Test
        @DisplayName("分隔线不被识别为章节")
        void testSeparatorLines() {
            String novel = """
                    第一章 开始
                    正文内容
                    ==========
                    更多正文
                    第二章 继续
                    后续内容
                    """;

            List<ChapterParseResult> chapters = parser.parseAll(stream(novel));
            assertEquals(2, chapters.size());
            assertTrue(chapters.get(0).content().contains("=========="));
        }
    }

    // ──── NovelReader integration ────

    @Nested
    @DisplayName("NovelReader集成")
    class NovelReaderTests {

        private final NovelReader reader = new NovelReader();

        @Test
        @DisplayName("readNovel返回完整Novel对象")
        void testReadNovel() {
            String content = """
                    第一章 开端
                    这是第一章的内容。

                    第二章 发展
                    这是第二章的内容。
                    """;

            var novel = reader.readNovel("测试小说", "测试作者",
                    "test.txt", 100, content);

            assertNotNull(novel);
            assertEquals("测试小说", novel.getTitle());
            assertEquals("测试作者", novel.getAuthor());
            assertEquals(2, novel.getChapterCount());
            assertEquals(2, novel.getChapters().size());
            assertEquals(content.length(), novel.getTotalChars());
        }

        @Test
        @DisplayName("getTableOfContents返回目录")
        void testGetTableOfContents() {
            String content = """
                    序章
                    序章内容。

                    第一章 开始
                    第一章内容。

                    尾声
                    尾声内容。
                    """;

            List<ChapterMeta> toc = reader.getTableOfContents(content);
            assertEquals(3, toc.size());
            assertEquals("序章", toc.get(0).title());
            assertEquals(ChapterType.PROLOGUE, toc.get(0).type());
        }

        @Test
        @DisplayName("空内容抛出异常")
        void testEmptyContentThrows() {
            assertThrows(Exception.class, () ->
                    reader.readNovel("空", "作者", "empty.txt", 0, ""));
        }
    }
}
