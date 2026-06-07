package com.novel2script.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novel2script.application.parser.NovelReader;
import com.novel2script.common.enums.NovelStatus;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Chapter;
import com.novel2script.domain.model.Novel;
import com.novel2script.infrastructure.mapper.ChapterMapper;
import com.novel2script.infrastructure.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Application service for novel upload and management.
 * Uses MyBatis-Plus for database persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;
    private final NovelReader novelReader;

    /**
     * Upload and register a novel from raw content.
     * Parses chapters from the content and stores them in the database.
     */
    @Transactional
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

        LocalDateTime now = LocalDateTime.now();

        // Parse chapters from sanitized content
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

        // Insert novel
        Novel novel = Novel.builder()
                .title(title)
                .author(author)
                .fileName(fileName)
                .fileSize(fileSize)
                .totalChars(totalChars)
                .chapterCount(chapters.size())
                .metadata(metadata)
                .status(NovelStatus.PARSED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        int inserted = novelMapper.insert(novel);
        if (inserted <= 0) {
            throw new BusinessException("DB_ERROR", "Failed to insert novel record");
        }

        // Insert chapters with the generated novel ID
        for (Chapter chapter : chapters) {
            chapter.setNovelId(novel.getId());
            chapter.setCreatedAt(now);
            chapterMapper.insert(chapter);
        }

        novel.setChapters(chapters);
        log.info("Novel registered: id={}, title='{}', chapters={}, chars={}",
                novel.getId(), novel.getTitle(), chapters.size(), totalChars);
        return novel;
    }

    /**
     * Find a novel by its ID, including its chapters.
     */
    public Optional<Novel> findById(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel != null) {
            List<Chapter> chapters = chapterMapper.selectList(
                    new LambdaQueryWrapper<Chapter>().eq(Chapter::getNovelId, novelId));
            novel.setChapters(chapters);
            log.debug("Found novel: id={}, title='{}'", novelId, novel.getTitle());
            return Optional.of(novel);
        }
        log.debug("Novel not found: id={}", novelId);
        return Optional.empty();
    }

    /**
     * List all novels (without chapters for performance).
     */
    public List<Novel> listAll() {
        LambdaQueryWrapper<Novel> query = new LambdaQueryWrapper<>();
        query.orderByDesc(Novel::getCreatedAt);
        return novelMapper.selectList(query);
    }

    /**
     * Delete a novel and all related data.
     * Cascade deletes are handled by database foreign keys.
     */
    @Transactional
    public void deleteNovel(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            log.warn("Novel not found for deletion: id={}", novelId);
            throw new BusinessException("NOVEL_NOT_FOUND", "Novel not found: id=" + novelId);
        }
        // DB cascade handles chapters, scripts, etc.
        int deleted = novelMapper.deleteById(novelId);
        if (deleted > 0) {
            log.info("Deleted novel: id={}, title='{}'", novelId, novel.getTitle());
        }
    }

    /**
     * Update novel status.
     */
    @Transactional
    public void updateStatus(Long novelId, NovelStatus status) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            log.warn("Novel not found for status update: id={}", novelId);
            return;
        }
        novel.setStatus(status);
        novel.setUpdatedAt(LocalDateTime.now());
        novelMapper.updateById(novel);
        log.info("Updated novel status: id={}, status={}", novelId, status);
    }

    // ── Encoding diagnostics & sanitization ──────────────

    private void logContentDiagnostics(String content) {
        String preview = content.length() > 200 ? content.substring(0, 200) + "…" : content;
        log.info("Content preview (first 200 chars):\n{}", preview);

        long replacementChars = content.chars().filter(c -> c == '�').count();
        if (replacementChars > 0) {
            log.warn("⚠️  Content contains {} Unicode replacement characters (U+FFFD) — encoding issue!", replacementChars);
        }

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
            log.warn("⚠️  Content: {:.1f}% Latin-1 supplement, {:.1f}% CJK — likely GBK incorrectly decoded as UTF-8!",
                    latinRatio * 100, cjkRatio * 100);
        } else if (cjkRatio > 0.05) {
            log.info("✅ Content encoding looks healthy: {:.1f}% CJK characters, {} replacement chars",
                    cjkRatio * 100, replacementChars);
        }

        Map<Character, Integer> freq = new LinkedHashMap<>();
        for (int i = 0; i < sampleSize; i++) {
            char c = content.charAt(i);
            if (c >= 0x0080 && c <= 0x00FF) {
                freq.merge(c, 1, Integer::sum);
            }
        }
        for (Map.Entry<Character, Integer> e : freq.entrySet()) {
            if ((double) e.getValue() / sampleSize > 0.05) {
                log.warn("⚠️  Character '{}' (U+{:04X}) appears {} times ({:.1f}%) — garbled text indicator",
                        e.getKey(), (int) e.getKey(), e.getValue(),
                        (double) e.getValue() / sampleSize * 100);
                break;
            }
        }
    }

    private String sanitizeContent(String content) {
        if (content == null || content.isEmpty()) return content;

        String sanitized = content;

        // 1. Remove BOM and null bytes
        sanitized = sanitized.replace("﻿", "").replace(" ", "");

        // 2. Normalize line endings
        sanitized = sanitized.replace("\r\n", "\n").replace("\r", "\n");

        // 3. Collapse excessive blank lines
        sanitized = sanitized.replaceAll("\\n{4,}", "\n\n\n");

        // 4. Replace Unicode private-use area characters
        StringBuilder cleaned = new StringBuilder(sanitized.length());
        int replaced = 0;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c >= '' && c <= '') {
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
