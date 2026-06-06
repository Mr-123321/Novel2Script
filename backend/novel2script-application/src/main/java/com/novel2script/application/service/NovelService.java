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

        // ── Encoding diagnostics ──────────────────────────
        logContentDiagnostics(rawContent);

        // ── Content sanitization ──────────────────────────
        String sanitized = sanitizeContent(rawContent);

        Long id = idGenerator.getAndIncrement();
        LocalDateTime now = LocalDateTime.now();

        // Parse chapters from sanitized content using ChapterParser
        List<Chapter> chapters;
        try {
            chapters = novelReader.parseChapters(sanitized);
            log.info("Parsed {} chapters from novel content", chapters.size());
        } catch (Exception e) {
            log.warn("Chapter parsing failed, storing as single chapter: {}", e.getMessage());
            Chapter fallbackChapter = Chapter.builder()
                    .chapterNumber(1)
                    .title(title)
                    .content(sanitized)
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
        metadata.put("rawContent", sanitized);

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

    // ── Encoding diagnostics & sanitization ──────────────

    /**
     * Log encoding diagnostics for the uploaded content.
     * Helps identify encoding issues before content reaches AI agents.
     */
    private void logContentDiagnostics(String content) {
        // Content preview (first 200 chars)
        String preview = content.length() > 200 ? content.substring(0, 200) + "…" : content;
        log.info("Content preview (first 200 chars):\n{}", preview);

        // Check for replacement characters (U+FFFD) — definitive sign of bad decode
        long replacementChars = content.chars().filter(c -> c == '�').count();
        if (replacementChars > 0) {
            log.warn("⚠️  Content contains {} Unicode replacement characters (U+FFFD) — encoding issue!", replacementChars);
        }

        // Check for common GBK-garbled-as-UTF8 patterns
        // When GBK text is decoded as UTF-8, many Chinese chars become Latin-1 supplement chars
        int sampleSize = Math.min(content.length(), 2000);
        int latinSuppCount = 0;
        int cjkCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            char c = content.charAt(i);
            if (c >= 0x00C0 && c <= 0x00FF) latinSuppCount++;
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) cjkCount++;
        }
        double latinRatio = (double) latinSuppCount / sampleSize;
        double cjkRatio = (double) cjkCount / sampleSize;

        if (latinRatio > 0.10 && cjkRatio < 0.02 && replacementChars == 0) {
            log.warn("⚠️  Content: {:.1f}% Latin-1 supplement, {:.1f}% CJK — "
                    + "likely GBK text incorrectly decoded as UTF-8! "
                    + "AI dialogue generation may produce garbled output.",
                    latinRatio * 100, cjkRatio * 100);
        } else if (cjkRatio > 0.05) {
            log.info("✅ Content encoding looks healthy: {:.1f}% CJK characters, {} replacement chars",
                    cjkRatio * 100, replacementChars);
        }

        // Frequency analysis for unusual character clusters (garbled text pattern)
        Map<Character, Integer> freq = new LinkedHashMap<>();
        for (int i = 0; i < sampleSize; i++) {
            char c = content.charAt(i);
            if (c >= 0x0080 && c <= 0x00FF) {
                freq.merge(c, 1, Integer::sum);
            }
        }
        // If any single Latin-1 char appears >5% of the time, it's likely garbled
        for (Map.Entry<Character, Integer> e : freq.entrySet()) {
            if ((double) e.getValue() / sampleSize > 0.05) {
                log.warn("⚠️  Character '{}' (U+{:04X}) appears {} times ({:.1f}%) — garbled text indicator",
                        e.getKey(), (int) e.getKey(), e.getValue(),
                        (double) e.getValue() / sampleSize * 100);
                break; // report only the first anomaly
            }
        }
    }

    /**
     * Sanitize content before storage and AI processing.
     * Handles common encoding artifacts and normalizes whitespace.
     *
     * <p>Operations:
     * <ul>
     *   <li>Remove null bytes and BOM</li>
     *   <li>Normalize Unicode private-use characters</li>
     *   <li>Replace common garbled character sequences</li>
     *   <li>Normalize line endings to \n</li>
     * </ul>
     */
    private String sanitizeContent(String content) {
        if (content == null || content.isEmpty()) return content;

        String sanitized = content;

        // 1. Remove BOM (Byte Order Mark) and null bytes
        sanitized = sanitized.replace("﻿", "")
                .replace(" ", "");

        // 2. Normalize line endings: \r\n → \n, standalone \r → \n
        sanitized = sanitized.replace("\r\n", "\n")
                .replace("\r", "\n");

        // 3. Collapse 3+ consecutive blank lines into 2
        sanitized = sanitized.replaceAll("\\n{4,}", "\n\n\n");

        // 4. Replace Unicode private-use area characters (often encoding artifacts)
        StringBuilder cleaned = new StringBuilder(sanitized.length());
        int replaced = 0;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c >= '' && c <= '') {
                // Private Use Area — likely encoding artifact, replace with space
                cleaned.append(' ');
                replaced++;
            } else {
                cleaned.append(c);
            }
        }
        if (replaced > 0) {
            log.warn("Sanitized {} private-use-area characters (U+E000–U+F8FF)", replaced);
        }

        String result = cleaned.toString();

        if (!result.equals(content)) {
            log.info("Content sanitized: original={} chars, cleaned={} chars", content.length(), result.length());
        }

        return result;
    }
}
