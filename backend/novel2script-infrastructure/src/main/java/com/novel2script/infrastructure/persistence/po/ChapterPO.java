package com.novel2script.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chapters")
public class ChapterPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private Integer chapterNumber;

    private String title;

    private String content;

    private Integer charCount;

    private Long startOffset;

    private Long endOffset;

    private String embeddingId;

    private String status;

    private LocalDateTime createdAt;
}
