package com.novel2script.application.parser;

import com.novel2script.common.enums.ChapterType;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streaming chapter parser for Chinese web novels.
 * <p>
 * Supports 1M+ character novels without OOM by reading line-by-line and
 * returning chapters lazily via {@link Iterator}. Detects chapter boundaries
 * using comprehensive regex patterns covering Chinese, English, and mixed
 * formats.
 * </p>
 *
 * <h3>Supported formats</h3>
 * <ul>
 *   <li>第X章 / 第XX章 / 第一章 (Chinese numerals)</li>
 *   <li>第X回 / 第X节 / 第X卷</li>
 *   <li>Chapter 1 / Chapter 10</li>
 *   <li>CHAPTER I / CHAPTER II (Roman numerals)</li>
 *   <li>序章 / 楔子 / 尾声 / 终章 / 番外</li>
 *   <li>Prologue / Epilogue</li>
 *   <li>Volume + Chapter: 第X卷 第X章</li>
 *   <li>Numeric headers: 1. / 1) / 1、</li>
 * </ul>
 */
@Slf4j
public class ChapterParser {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    // ──── Pre-compiled regex patterns ────

    /**
     * Primary chapter header pattern.
     * Captures: 第X章, Chapter X, CHAPTER I, 序章, Prologue, etc.
     */
    private static final Pattern CHAPTER_HEADER = Pattern.compile(
            "^\\s*" +
            "(?:第\\s*[0-9零一二三四五六七八九十百千万]+\\s*[章回卷节]" +           // 第X章/回/卷/节
            "|第\\s*[0-9零一二三四五六七八九十百千万]+\\s*[卷]" +                     // 第X卷
            "|第?\\s*[0-9零一二三四五六七八九十百千万]+\\s*卷\\s*.*[章回]" +          // X卷 第X章
            "|(?:序|楔|引|前)[章子言]?" +                                               // 序章/楔子/引言/前言
            "|终[章篇结]?" +                                                            // 终章/终篇
            "|尾[声章]?" +                                                              // 尾声
            "|番外[篇章]?" +                                                            // 番外/番外篇
            "|后记" +                                                                    // 后记
            "|补遗" +                                                                    // 补遗
            "|Chapter\\s+\\d+" +                                                        // Chapter 1
            "|CHAPTER\\s+[IVXLCDM]+" +                                                  // CHAPTER I
            "|(?:Vol|Volume)\\.?\\s*\\d+" +                                             // Vol.1
            "|[Pp]rologue" +                                                             // Prologue
            "|[Ee]pilogue" +                                                             // Epilogue
            "|[Ii]nterlude" +                                                            // Interlude
            ")"
    );

    /**
     * Numeric header: "1.", "1)", "1、" at line start.
     * Only matches when the line is short (≤60 chars) to avoid false positives
     * on numbered lists or dialog.
     */
    private static final Pattern NUMERIC_HEADER = Pattern.compile(
            "^\\s*\\d+[.\\)、]\\s*.{1,60}$"
    );

    /**
     * Chinese numeral + 、header: "一、开始", "二、继续".
     * Matches typical Chinese outline-style chapter markers.
     */
    private static final Pattern CHINESE_NUMERAL_DUN_HEADER = Pattern.compile(
            "^\\s*[零一二三四五六七八九十百千万]+[、]\\s*.{1,60}$"
    );

    /**
     * Pre-chapter decorative lines (separators like "=====", "------").
     */
    private static final Pattern DECORATIVE_LINE = Pattern.compile(
            "^\\s*[-=*_#~]{3,}\\s*$"
    );

    // ──── Public API ────

    /**
     * Stream chapters lazily from an {@link InputStream}.
     * Each call to {@code next()} reads until the next chapter boundary.
     *
     * @param input the novel content
     * @return a lazy iterator of {@link ChapterParseResult}
     */
    public Iterator<ChapterParseResult> parse(InputStream input) {
        return new ChapterIterator(new BufferedReader(
                new InputStreamReader(input, DEFAULT_CHARSET)));
    }

    /**
     * Stream chapters lazily from a file path.
     */
    public Iterator<ChapterParseResult> parse(Path filePath) throws IOException {
        return parse(Files.newInputStream(filePath));
    }

    /**
     * Stream chapters lazily from a string.
     */
    public Iterator<ChapterParseResult> parse(String content) {
        return parse(new ByteArrayInputStream(content.getBytes(DEFAULT_CHARSET)));
    }

    /**
     * Parse all chapters eagerly into a list.
     * Use {@link #parse} for large novels to avoid OOM.
     */
    public List<ChapterParseResult> parseAll(InputStream input) {
        List<ChapterParseResult> results = new ArrayList<>();
        Iterator<ChapterParseResult> it = parse(input);
        while (it.hasNext()) {
            results.add(it.next());
        }
        return results;
    }

    /**
     * Parse only the table-of-contents: chapter headers without body content.
     */
    public List<ChapterMeta> parseTOC(InputStream input) {
        List<ChapterMeta> toc = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, DEFAULT_CHARSET))) {

            String line;
            long offset = 0;
            int chapterNum = 0;

            while ((line = reader.readLine()) != null) {
                int lineLen = line.length() + 1; // +1 for newline

                ChapterHeaderInfo header = detectHeader(line);
                if (header != null) {
                    chapterNum++;
                    toc.add(new ChapterMeta(
                            chapterNum,
                            header.title(),
                            offset,
                            header.type()
                    ));
                }

                offset += lineLen;
            }
        } catch (IOException e) {
            log.error("Failed to parse TOC", e);
        }
        return toc;
    }

    /**
     * Build a full string from the input stream (for tests / small novels).
     */
    public static String readAll(InputStream input) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, DEFAULT_CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    // ──── Header Detection ────

    /**
     * Try to detect a chapter header in the given line.
     *
     * @return header info if this line looks like a chapter header, {@code null} otherwise
     */
    static ChapterHeaderInfo detectHeader(String line) {
        if (line == null || line.isBlank()) return null;

        // Skip decorative separator lines
        if (DECORATIVE_LINE.matcher(line).matches()) return null;

        String trimmed = line.strip();

        // 1. Primary pattern match
        Matcher m = CHAPTER_HEADER.matcher(trimmed);
        if (m.find() && m.start() == 0 && isValidHeaderBoundary(trimmed, m.end())) {
            return new ChapterHeaderInfo(trimmed, classifyType(trimmed));
        }

        // 2. Chinese numeral + 、header (e.g., "一、开始")
        if (CHINESE_NUMERAL_DUN_HEADER.matcher(trimmed).matches()) {
            return new ChapterHeaderInfo(trimmed, ChapterType.NORMAL);
        }

        // 3. Numeric header (fallback, with length guard)
        if (NUMERIC_HEADER.matcher(trimmed).matches()) {
            // Only treat as chapter if the line is short enough and looks like a title
            String rest = trimmed.replaceFirst("^\\s*\\d+[.\\)、]\\s*", "");
            if (rest.length() > 0 && rest.length() <= 40) {
                return new ChapterHeaderInfo(trimmed, ChapterType.NORMAL);
            }
        }

        return null;
    }

    /**
     * Check that the regex match ends at a word boundary — the character
     * after the match must be whitespace, punctuation, or end-of-string.
     * Prevents false positives like "第一章正文" where "第一章" is matched
     * inside a larger word.
     */
    private static boolean isValidHeaderBoundary(String text, int matchEnd) {
        if (matchEnd >= text.length()) return true; // match ends at string end
        char next = text.charAt(matchEnd);
        // Whitespace, punctuation/dash, or end = valid boundary
        return Character.isWhitespace(next)
                || isPunctuation(next);
    }

    /**
     * Returns true for common Chinese/English punctuation and separators.
     */
    private static boolean isPunctuation(char c) {
        return switch (c) {
            case '，', '。', '！', '？', '、', '：', '；', '（', '）', '《', '》',
                 '「', '」', '『', '』', '【', '】', '—', '…', '～', '·',
                 '.', ',', '!', '?', ':', ';', '(', ')', '[', ']', '{', '}',
                 '-', '"', '\'', '/', '\\', '|', '@', '#', '$', '%', '^',
                 '&', '*', '+', '=', '<', '>', '~', '`', '_' -> true;
            default -> false;
        };
    }

    /**
     * Classify the chapter type based on the header text.
     */
    static ChapterType classifyType(String header) {
        String lower = header.toLowerCase().strip();

        // Prologue patterns
        if (lower.startsWith("序") || lower.startsWith("楔") || lower.startsWith("引")
                || lower.startsWith("前") || lower.startsWith("prologue")
                || lower.startsWith("foreword")) {
            return ChapterType.PROLOGUE;
        }

        // Epilogue patterns
        if (lower.startsWith("终") || lower.startsWith("尾")
                || lower.startsWith("epilogue") || lower.startsWith("afterword")
                || lower.startsWith("后记") || lower.startsWith("结语")
                || lower.startsWith("补遗")) {
            return ChapterType.EPILOGUE;
        }

        // Interlude / side story
        if (lower.startsWith("番外") || lower.startsWith("interlude")
                || lower.startsWith("外传") || lower.startsWith("特别篇")) {
            return ChapterType.INTERLUDE;
        }

        return ChapterType.NORMAL;
    }

    // ──── Inner Classes ────

    /**
     * Parsed chapter header metadata.
     */
    record ChapterHeaderInfo(String title, ChapterType type) {}

    /**
     * Lazy iterator that reads chapters one at a time from a {@link BufferedReader}.
     * Only the current chapter's content is held in memory.
     */
    private static class ChapterIterator implements Iterator<ChapterParseResult> {

        private final BufferedReader reader;
        private ChapterParseResult nextChapter;
        private boolean finished;
        private boolean eof;
        private long currentOffset;
        private long lineStartOffset;
        private int chapterNumber;
        private String pendingLine; // first line of a header detected by look-ahead

        ChapterIterator(BufferedReader reader) {
            this.reader = reader;
            this.nextChapter = null;
            this.finished = false;
            this.eof = false;
            this.currentOffset = 0;
            this.lineStartOffset = 0;
            this.chapterNumber = 0;
            this.pendingLine = null;
        }

        @Override
        public boolean hasNext() {
            if (finished) return false;
            if (nextChapter != null) return true;
            try {
                nextChapter = readNextChapter();
                if (nextChapter == null) {
                    finished = true;
                    closeReader();
                }
                return nextChapter != null;
            } catch (Exception e) {
                log.error("Error reading next chapter", e);
                finished = true;
                closeReader();
                return false;
            }
        }

        @Override
        public ChapterParseResult next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more chapters");
            }
            ChapterParseResult ch = nextChapter;
            nextChapter = null;
            return ch;
        }

        private ChapterParseResult readNextChapter() throws IOException {
            ChapterHeaderInfo currentHeader = null;
            StringBuilder content = new StringBuilder();
            long chapterStartOffset = -1;
            int contentCharCount = 0;

            // If we have a pending header line from a previous look-ahead, use it
            if (pendingLine != null) {
                ChapterHeaderInfo info = detectHeader(pendingLine);
                if (info != null) {
                    currentHeader = info;
                    chapterStartOffset = lineStartOffset;
                    // The header line itself is part of the content
                    content.append(pendingLine).append('\n');
                    contentCharCount += pendingLine.length() + 1;
                    currentOffset = lineStartOffset + pendingLine.length() + 1;
                } else {
                    // Wasn't actually a header, include in content
                    content.append(pendingLine).append('\n');
                    contentCharCount += pendingLine.length() + 1;
                    currentOffset = lineStartOffset + pendingLine.length() + 1;
                    if (chapterStartOffset < 0) {
                        chapterStartOffset = lineStartOffset;
                    }
                }
                pendingLine = null;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                lineStartOffset = currentOffset;
                int lineLen = line.length() + 1; // including newline
                currentOffset += lineLen;

                ChapterHeaderInfo header = detectHeader(line);

                if (header != null) {
                    if (currentHeader != null) {
                        // Found a new chapter header → save current chapter and look ahead
                        pendingLine = line;
                        currentOffset = lineStartOffset;
                        return buildChapter(currentHeader, content, contentCharCount,
                                chapterStartOffset, lineStartOffset);
                    } else if (hasNonBlankContent(content)) {
                        // First header found, but preamble content exists → emit preamble first
                        pendingLine = line;
                        currentOffset = lineStartOffset;
                        return buildChapter(
                                new ChapterHeaderInfo("序章", ChapterType.PROLOGUE),
                                content, contentCharCount,
                                chapterStartOffset, lineStartOffset);
                    } else {
                        // First header found, no preceding content
                        currentHeader = header;
                        chapterStartOffset = lineStartOffset;
                    }
                }

                if (chapterStartOffset < 0 && !line.isBlank()) {
                    chapterStartOffset = lineStartOffset;
                }

                content.append(line).append('\n');
                contentCharCount += lineLen;
            }

            // EOF reached
            eof = true;

            if (currentHeader != null || content.length() > 0) {
                if (currentHeader == null) {
                    // Entire file had no chapter headers — treat as single chapter
                    currentHeader = new ChapterHeaderInfo("第1章", ChapterType.NORMAL);
                    if (chapterStartOffset < 0) chapterStartOffset = 0;
                }
                return buildChapter(currentHeader, content, contentCharCount,
                        chapterStartOffset, currentOffset);
            }

            return null;
        }

        /**
         * Returns true if the content buffer has non-whitespace text.
         */
        private boolean hasNonBlankContent(StringBuilder sb) {
            for (int i = 0; i < sb.length(); i++) {
                if (!Character.isWhitespace(sb.charAt(i))) {
                    return true;
                }
            }
            return false;
        }

        private ChapterParseResult buildChapter(ChapterHeaderInfo header,
                                                 StringBuilder content,
                                                 int charCount,
                                                 long startOffset,
                                                 long endOffset) {
            chapterNumber++;
            return new ChapterParseResult(
                    chapterNumber,
                    header.title(),
                    content.toString(),
                    charCount,
                    startOffset,
                    endOffset,
                    header.type()
            );
        }

        private void closeReader() {
            try {
                reader.close();
            } catch (IOException ignored) {
                // ignore close errors
            }
        }
    }
}
