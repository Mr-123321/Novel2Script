package com.novel2script.application.parser;

import com.novel2script.common.enums.ChapterType;

/**
 * Lightweight metadata for a chapter, returned by {@link ChapterParser#parseTOC}.
 * Contains only header information without the body content.
 */
public record ChapterMeta(
        int chapterNumber,
        String title,
        long startOffset,
        ChapterType type
) {}
