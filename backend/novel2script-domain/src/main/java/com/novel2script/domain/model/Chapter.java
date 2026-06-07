package com.novel2script.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("chapters")
public class Chapter {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("novel_id")
    private Long novelId;

    @TableField("chapter_number")
    private int chapterNumber;

    private String title;
    private String content;

    @TableField("char_count")
    private int charCount;

    @TableField("start_offset")
    private long startOffset;

    @TableField("end_offset")
    private long endOffset;

    @TableField("embedding_id")
    private String embeddingId;

    private String status;  // RAW, PARSED, EMBEDDED

    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getDisplayName() {
        if (title != null && !title.isBlank()) {
            return String.format("第%d章 %s", chapterNumber, title);
        }
        return String.format("第%d章", chapterNumber);
    }
}
