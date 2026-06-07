package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
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
@TableName(value = "novels", autoResultMap = true)
public class Novel {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;
    private String author;

    @TableField("file_name")
    private String fileName;

    @TableField("file_size")
    private long fileSize;

    @TableField("total_chars")
    private int totalChars;

    @TableField("chapter_count")
    private int chapterCount;

    private NovelStatus status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /** Chapters are stored in their own table; not a database column */
    @TableField(exist = false)
    @Builder.Default
    private List<Chapter> chapters = new ArrayList<>();

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
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
