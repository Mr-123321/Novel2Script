package com.novel2script.domain.model;

import com.novel2script.common.enums.NovelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Novel aggregate root — represents an uploaded novel in the domain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Novel {

    private Long id;
    private String title;
    private String author;
    private String fileName;
    private long fileSize;
    private int totalChars;
    private int chapterCount;
    private NovelStatus status;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private List<Chapter> chapters = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Domain logic ---

    public boolean isProcessable() {
        return status == NovelStatus.PARSED && chapterCount >= 3;
    }

    public boolean isComplete() {
        return status == NovelStatus.COMPLETED;
    }

    public void startProcessing() {
        if (status != NovelStatus.PARSED) {
            throw new IllegalStateException("Novel must be PARSED before processing. Current: " + status);
        }
        this.status = NovelStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = NovelStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = NovelStatus.FAILED;
    }

    public String getTotalCharsFormatted() {
        if (totalChars >= 10_000) {
            return String.format("%.1f 万字", totalChars / 10_000.0);
        }
        return totalChars + " 字";
    }
}
