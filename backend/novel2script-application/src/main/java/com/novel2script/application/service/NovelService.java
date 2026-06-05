package com.novel2script.application.service;

import com.novel2script.common.enums.NovelStatus;
import com.novel2script.common.exception.BusinessException;
import com.novel2script.domain.model.Novel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application service for novel upload and management.
 */
@Slf4j
@Service
public class NovelService {

    /**
     * Upload and register a novel from raw content.
     */
    public Novel uploadNovel(String title, String author, String fileName,
                             long fileSize, String rawContent) {
        log.info("Uploading novel: title='{}', author='{}', fileSize={}", title, author, fileSize);

        int totalChars = rawContent.length();
        if (totalChars > 1_000_000) {
            throw new BusinessException("NOVEL_TOO_LONG",
                    String.format("Novel exceeds max length: %d > 1,000,000", totalChars));
        }

        Novel novel = Novel.builder()
                .title(title)
                .author(author)
                .fileName(fileName)
                .fileSize(fileSize)
                .totalChars(totalChars)
                .status(NovelStatus.UPLOADED)
                .build();

        log.info("Novel registered: id={}, title='{}', chars={}", novel.getId(), novel.getTitle(), totalChars);
        return novel;
    }

    /**
     * Find a novel by its ID.
     */
    public Optional<Novel> findById(Long novelId) {
        // TODO: delegate to repository
        log.debug("Finding novel by id={}", novelId);
        return Optional.empty();
    }

    /**
     * List all novels.
     */
    public List<Novel> listAll() {
        // TODO: delegate to repository
        return List.of();
    }

    /**
     * Delete a novel and all related data.
     */
    public void deleteNovel(Long novelId) {
        log.info("Deleting novel id={}", novelId);
        // TODO: delegate to repository
    }

    /**
     * Update novel status.
     */
    public void updateStatus(Long novelId, NovelStatus status) {
        log.info("Updating novel id={} status to {}", novelId, status);
        // TODO: delegate to repository
    }
}
