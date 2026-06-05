package com.novel2script.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single chapter within a novel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {

    private Long id;
    private Long novelId;
    private int chapterNumber;
    private String title;
    private String content;
    private int charCount;
    private long startOffset;
    private long endOffset;
    private String embeddingId;
    private String status;  // RAW, PARSED, EMBEDDED

    private LocalDateTime createdAt;

    public String getDisplayName() {
        if (title != null && !title.isBlank()) {
            return String.format("第%d章 %s", chapterNumber, title);
        }
        return String.format("第%d章", chapterNumber);
    }
}
