package com.novel2script.application.parser;

import com.novel2script.common.enums.NovelStatus;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Novel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * High-level service for reading novels, parsing chapters, and computing statistics.
 * Wraps {@link ChapterParser} with domain model conversion and error handling.
 */
@Slf4j
@Service
public class NovelReader {

    private final ChapterParser parser;

    public NovelReader() {
        this.parser = new ChapterParser();
    }

    /**
     * Read a novel from raw text content, producing a domain {@link Novel}
     * with all chapters parsed.
     *
     * @param title    novel title
     * @param author   author name (nullable)
     * @param fileName original file name
     * @param fileSize file size in bytes
     * @param content  raw text content (UTF-8)
     * @return populated Novel with chapters
     * @throws BusinessException if the content is empty or parsing fails
     */
    public Novel readNovel(String title, String author, String fileName,
                           long fileSize, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("EMPTY_CONTENT", "Novel content is empty");
        }

        log.info("Reading novel: title='{}', author='{}', size={} bytes", title, author, fileSize);

        int totalChars = content.length();
        if (totalChars > 10_000_000) {
            log.warn("Very large novel detected: {} chars. Consider streaming.", totalChars);
        }

        List<Chapter> chapters = parseChapters(content);

        Novel novel = Novel.builder()
                .title(title)
                .author(author != null ? author : "未知")
                .fileName(fileName)
                .fileSize(fileSize)
                .totalChars(totalChars)
                .chapterCount(chapters.size())
                .chapters(chapters)
                .status(NovelStatus.PARSED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        log.info("Novel parsed: {} chapters, {} total chars", chapters.size(), totalChars);
        return novel;
    }

    /**
     * Read a novel from an {@link InputStream}.
     */
    public Novel readNovel(String title, String author, String fileName,
                           long fileSize, InputStream inputStream) {
        String content = ChapterParser.readAll(inputStream);
        return readNovel(title, author, fileName, fileSize, content);
    }

    /**
     * Parse chapters from raw content using the streaming parser,
     * converting results to domain {@link Chapter} objects.
     */
    public List<Chapter> parseChapters(String content) {
        List<Chapter> chapters = new ArrayList<>();

        InputStream input = new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));
        Iterator<ChapterParseResult> it = parser.parse(input);

        while (it.hasNext()) {
            ChapterParseResult result = it.next();
            Chapter chapter = Chapter.builder()
                    .chapterNumber(result.chapterNumber())
                    .title(result.title())
                    .content(result.content())
                    .charCount(result.charCount())
                    .startOffset(result.startOffset())
                    .endOffset(result.endOffset())
                    .status("PARSED")
                    .createdAt(LocalDateTime.now())
                    .build();
            chapters.add(chapter);
        }

        return chapters;
    }

    /**
     * Parse chapters from a novel, populating the novel's chapter list.
     */
    public List<Chapter> parseChapters(Novel novel) {
        if (novel == null) {
            throw new BusinessException("NULL_NOVEL", "Novel cannot be null");
        }
        // This requires the novel to have its raw content accessible.
        // In a real implementation, content would be loaded from storage.
        log.warn("parseChapters(Novel) requires novel content. " +
                 "Use parseChapters(String) for direct content parsing.");
        return novel.getChapters();
    }

    /**
     * Get the table of contents from novel content without reading the full body.
     */
    public List<ChapterMeta> getTableOfContents(String content) {
        return parser.parseTOC(new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Count total characters in novel content.
     */
    public int countTotalChars(String content) {
        return content != null ? content.length() : 0;
    }

    /**
     * Count total characters in a {@link Novel}.
     */
    public int countTotalChars(Novel novel) {
        return novel != null ? novel.getTotalChars() : 0;
    }
}
