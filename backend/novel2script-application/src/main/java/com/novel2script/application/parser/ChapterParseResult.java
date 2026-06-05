package com.novel2script.application.parser;

import com.novel2script.common.enums.ChapterType;

/**
 * Result of parsing a single chapter from a novel.
 * Contains the full text content and byte-range metadata.
 */
public record ChapterParseResult(
        int chapterNumber,
        String title,
        String content,
        int charCount,
        long startOffset,
        long endOffset,
        ChapterType type
) {

    /**
     * Returns a human-readable display name like "第3章 穿越".
     */
    public String getDisplayName() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return "第" + chapterNumber + "章";
    }
}
