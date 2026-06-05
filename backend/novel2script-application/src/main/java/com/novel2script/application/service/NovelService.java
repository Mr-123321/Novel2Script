package com.novel2script.application.service;

import com.novel2script.application.parser.NovelReader;
import com.novel2script.common.enums.NovelStatus;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Novel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application service for novel upload and management.
 * Uses in-memory storage as a development fallback.
 */
@Slf4j
@Service
public class NovelService {

    private final Map<Long, Novel> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final NovelReader novelReader;

    public NovelService(NovelReader novelReader) {
        this.novelReader = novelReader;
    }

    /**
     * Upload and register a novel from raw content.
     * Parses chapters from the content and stores them in the Novel.
     */
    public Novel uploadNovel(String title, String author, String fileName,
                             long fileSize, String rawContent) {
        log.info("Uploading novel: title='{}', author='{}', fileSize={}", title, author, fileSize);

        int totalChars = rawContent.length();
        if (totalChars > 1_000_000) {
            throw new BusinessException("NOVEL_TOO_LONG",
                    String.format("Novel exceeds max length: %d > 1,000,000", totalChars));
        }

        // Log content preview for encoding diagnosis (first 200 chars)
        String preview = rawContent.length() > 200 ? rawContent.substring(0, 200) + "…" : rawContent;
        log.info("Content preview (first 200 chars):\n{}", preview);

        // Encoding health check — look for garbled character markers
        long replacementChars = rawContent.chars().filter(c -> c == '�').count();
        if (replacementChars > 0) {
            log.warn("⚠️  Content contains {} Unicode replacement characters (U+FFFD) — possible encoding issue!", replacementChars);
        }
        // Check for common GBK-garbled-as-UTF8 patterns (e.g., "é" appearing frequently)
        int latinGarbled = 0;
        for (int i = 0; i < Math.min(rawContent.length(), 1000); i++) {
            char c = rawContent.charAt(i);
            if ((c >= 0x00C0 && c <= 0x00FF) && !Character.isLetter(c)) latinGarbled++;
        }
        if (latinGarbled > 20 && !rawContent.contains("café") && !rawContent.contains("fiancée")) {
            log.warn("⚠️  Content contains many Latin-1 supplement chars — may be GBK text decoded as UTF-8!");
        }

        Long id = idGenerator.getAndIncrement();
        LocalDateTime now = LocalDateTime.now();

        // Parse chapters from raw content using ChapterParser
        List<Chapter> chapters;
        try {
            chapters = novelReader.parseChapters(rawContent);
            log.info("Parsed {} chapters from novel content", chapters.size());
        } catch (Exception e) {
            log.warn("Chapter parsing failed, storing as single chapter: {}", e.getMessage());
            Chapter fallbackChapter = Chapter.builder()
                    .chapterNumber(1)
                    .title(title)
                    .content(rawContent)
                    .charCount(totalChars)
                    .startOffset(0)
                    .endOffset(totalChars)
                    .status("PARSED")
                    .createdAt(now)
                    .build();
            chapters = List.of(fallbackChapter);
        }

        // Store raw content in metadata for agent access
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("rawContent", rawContent);

        Novel novel = Novel.builder()
                .id(id)
                .title(title)
                .author(author)
                .fileName(fileName)
                .fileSize(fileSize)
                .totalChars(totalChars)
                .chapterCount(chapters.size())
                .chapters(chapters)
                .metadata(metadata)
                .status(NovelStatus.PARSED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        store.put(id, novel);
        log.info("Novel registered: id={}, title='{}', chapters={}, chars={}",
                novel.getId(), novel.getTitle(), chapters.size(), totalChars);
        return novel;
    }

    /**
     * Find a novel by its ID.
     */
    public Optional<Novel> findById(Long novelId) {
        Novel novel = store.get(novelId);
        if (novel != null) {
            log.debug("Found novel: id={}, title='{}'", novelId, novel.getTitle());
            return Optional.of(novel);
        }
        log.debug("Novel not found: id={}", novelId);
        return Optional.empty();
    }

    /**
     * List all novels.
     */
    public List<Novel> listAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * Delete a novel and all related data.
     */
    public void deleteNovel(Long novelId) {
        Novel removed = store.remove(novelId);
        if (removed != null) {
            log.info("Deleted novel: id={}, title='{}'", novelId, removed.getTitle());
        } else {
            log.warn("Novel not found for deletion: id={}", novelId);
        }
    }

    /**
     * Update novel status.
     */
    public void updateStatus(Long novelId, NovelStatus status) {
        Novel novel = store.get(novelId);
        if (novel != null) {
            novel.setStatus(status);
            novel.setUpdatedAt(LocalDateTime.now());
            log.info("Updated novel status: id={}, status={}", novelId, status);
        } else {
            log.warn("Novel not found for status update: id={}", novelId);
        }
    }
}
