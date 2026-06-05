package com.novel2script.domain.event;

import java.time.Instant;

/**
 * Domain event emitted when a novel has been fully parsed into chapters.
 */
public record NovelParsedEvent(
        long novelId,
        String title,
        int chapterCount,
        int totalChars,
        Instant occurredAt
) {
    public NovelParsedEvent(long novelId, String title, int chapterCount, int totalChars) {
        this(novelId, title, chapterCount, totalChars, Instant.now());
    }

    @Override
    public String toString() {
        return String.format("NovelParsedEvent[novelId=%d, title='%s', chapters=%d, chars=%d]",
                novelId, title, chapterCount, totalChars);
    }
}
